package com.github.pandafolks.panda.participant

import cats.implicits.toTraverseOps
import com.github.pandafolks.panda.participant.ParticipantsRouting.{GROUPS_NAME, PARTICIPANTS_NAME, ParticipantsModificationResultPayload}
import com.github.pandafolks.panda.participant.event.ParticipantEventService
import com.github.pandafolks.panda.routes.Group
import com.github.pandafolks.panda.user.{SubRoutingWithAuth, User}
import com.github.pandafolks.panda.utils.routing.RoutesResultParser.{parseErrors, parseSuccessfulResults}
import com.github.pandafolks.panda.utils.routing.SubRouting.{API_NAME, API_VERSION_1}
import io.circe.generic.auto._
import monix.eval.Task
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.dsl.Http4sDsl
import org.http4s._

final class ParticipantsRouting(
    private val participantEventService: ParticipantEventService,
    private val participantsCache: ParticipantsCache
) extends Http4sDsl[Task]
    with SubRoutingWithAuth {

  private val routes = AuthedRoutes.of[User, Task] {
    case _ @GET -> Root / API_NAME / API_VERSION_1 / GROUPS_NAME as _ =>
      participantsCache.getAllGroups.flatMap {
        case groups if groups.nonEmpty => Ok(Seq(groups: _*))
        case _                         => Task.eval(Response.notFound)
      }

    case _ @GET -> Root / API_NAME / API_VERSION_1 / PARTICIPANTS_NAME :? OptionalFilterQueryParamMatcher(
          maybeFilter
        ) as _ =>
      handleParticipantsResponse(
        maybeFilter
          .map(_.getParticipants)
          .getOrElse(participantsCache.getAllParticipants)
      )

    case _ @GET -> Root / API_NAME / API_VERSION_1 / PARTICIPANTS_NAME / group :? OptionalFilterQueryParamMatcher(
          maybeFilter
        ) as _ =>
      handleParticipantsResponse(
        maybeFilter
          .map(_.getParticipants(Group(group)))
          .getOrElse(participantsCache.getParticipantsAssociatedWithGroup(Group(group)))
      )

    // participants modification endpoints:
    case req @ POST -> Root / API_NAME / API_VERSION_1 / PARTICIPANTS_NAME as _ =>
      for {
        participantPayload <- req.req.as[Seq[ParticipantModificationPayload]]
        saveResults <- participantPayload.map(participantEventService.createParticipant).sequence
        successfullySaved = parseSuccessfulResults(saveResults)
        response <- Ok(
          ParticipantsModificationResultPayload(
            message = s"Created successfully ${successfullySaved.size} participants out of ${saveResults.size} requested",
            successfulParticipantIdentifiers = successfullySaved,
            errors = parseErrors(saveResults)
          )
        )
      } yield response

    case req @ PUT -> Root / API_NAME / API_VERSION_1 / PARTICIPANTS_NAME as _ =>
      for {
        participantPayload <- req.req.as[Seq[ParticipantModificationPayload]]
        modifyResults <- participantPayload.map(participantEventService.modifyParticipant).sequence
        successfullyModified = parseSuccessfulResults(modifyResults)
        response <- Ok(
          ParticipantsModificationResultPayload(
            message = s"Modified successfully ${successfullyModified.size} participants out of ${modifyResults.size} requested",
            successfulParticipantIdentifiers = successfullyModified,
            errors = parseErrors(modifyResults)
          )
        )
      } yield response

    case req @ DELETE -> Root / API_NAME / API_VERSION_1 / PARTICIPANTS_NAME as _ =>
      for {
        payload <- req.req.as[Seq[String]]
        removeResults <- payload.map(participantEventService.removeParticipant).sequence
        successfullyRemoved = parseSuccessfulResults(removeResults)
        response <- Ok(
          ParticipantsModificationResultPayload(
            message = s"Removed successfully ${successfullyRemoved.size} participants out of ${removeResults.size} requested",
            successfulParticipantIdentifiers = successfullyRemoved,
            errors = parseErrors(removeResults)
          )
        )
      } yield response

  }

  private def handleParticipantsResponse(participants: Task[Seq[Participant]]): Task[Response[Task]] =
    participants.flatMap {
      case participants if participants.nonEmpty => Ok(Seq(participants: _*))
      case _                                     => Task.eval(Response.notFound)
    }

  override def getRoutesWithAuth: AuthedRoutes[User, Task] = routes

  implicit val participantCreationDtoDecoder: EntityDecoder[Task, ParticipantModificationPayload] =
    jsonOf[Task, ParticipantModificationPayload]
  implicit val participantCreationDtoSeqDecoder: EntityDecoder[Task, Seq[ParticipantModificationPayload]] =
    jsonOf[Task, Seq[ParticipantModificationPayload]]
  implicit val stringSeqEncoder: EntityDecoder[Task, Seq[String]] = jsonOf[Task, Seq[String]]

  implicit val createdParticipantsResultEncoder: EntityEncoder[Task, ParticipantsModificationResultPayload] =
    jsonEncoderOf[Task, ParticipantsModificationResultPayload]

  object ParticipantsFilter {

    sealed trait ParticipantsFilter {
      def getParticipants: Task[List[Participant]]

      def getParticipants(group: Group): Task[Vector[Participant]]
    }

    final case object AllParticipantsFilter extends ParticipantsFilter {
      override def getParticipants: Task[List[Participant]] = participantsCache.getAllParticipants

      override def getParticipants(group: Group): Task[Vector[Participant]] =
        participantsCache.getParticipantsAssociatedWithGroup(group)
    }

    final case object WorkingParticipantsFilter extends ParticipantsFilter {
      override def getParticipants: Task[List[Participant]] = participantsCache.getAllWorkingParticipants

      override def getParticipants(group: Group): Task[Vector[Participant]] =
        participantsCache.getWorkingParticipantsAssociatedWithGroup(group)
    }

    final case object HealthyParticipantsFilter extends ParticipantsFilter {
      override def getParticipants: Task[List[Participant]] = participantsCache.getAllHealthyParticipants

      override def getParticipants(group: Group): Task[Vector[Participant]] =
        participantsCache.getHealthyParticipantsAssociatedWithGroup(group)
    }

  }

  implicit val filterQueryParamDecoder: QueryParamDecoder[ParticipantsFilter.ParticipantsFilter] =
    QueryParamDecoder[String].map(_.toLowerCase match {
      case "working" => ParticipantsFilter.WorkingParticipantsFilter
      case "healthy" => ParticipantsFilter.HealthyParticipantsFilter
      case _         => ParticipantsFilter.AllParticipantsFilter
    })

  object OptionalFilterQueryParamMatcher extends OptionalQueryParamDecoderMatcher[ParticipantsFilter.ParticipantsFilter]("filter")

}

object ParticipantsRouting {

  final val PARTICIPANTS_NAME = "participants"
  final val GROUPS_NAME = "groups"

  final case class ParticipantsModificationResultPayload(
      message: String,
      successfulParticipantIdentifiers: List[String],
      errors: List[String]
  )

}
