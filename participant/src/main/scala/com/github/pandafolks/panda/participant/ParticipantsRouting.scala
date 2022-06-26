package com.github.pandafolks.panda.participant

import cats.implicits.toTraverseOps
import com.github.pandafolks.panda.participant.ManagementRouting.ParticipantsModificationResult
import com.github.pandafolks.panda.participant.dto.ParticipantModificationDto
import com.github.pandafolks.panda.participant.event.ParticipantEventService
import com.github.pandafolks.panda.routes.Group
import com.github.pandafolks.panda.user.{SubRoutingWithAuth, User}
import com.github.pandafolks.panda.utils.PersistenceError
import com.github.pandafolks.panda.utils.SubRouting.{API_NAME, API_VERSION_1}
import io.circe.generic.auto._
import monix.eval.Task
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.dsl.Http4sDsl
import org.http4s.{AuthedRoutes, EntityDecoder, EntityEncoder, QueryParamDecoder, Response}


final class ParticipantsRouting(private val participantEventService: ParticipantEventService,
                                private val participantsCache: ParticipantsCache) extends Http4sDsl[Task]
  with SubRoutingWithAuth {

  private val routes = AuthedRoutes.of[User, Task] {
    case _@GET -> Root / API_NAME / API_VERSION_1 / "groups" as _ =>
      participantsCache.getAllGroups.flatMap {
        case groups if groups.nonEmpty => Ok(Seq(groups: _*))
        case _ => Task.eval(Response.notFound)
      }

    case _@GET -> Root / API_NAME / API_VERSION_1 / "participants" :? OptionalFilterQueryParamMatcher(maybeFilter) as _ =>
      handleParticipantsResponse(maybeFilter
        .map(_.getParticipants)
        .getOrElse(participantsCache.getAllParticipants)
      )

    case _@GET -> Root / API_NAME / API_VERSION_1 / "participants" / group :? OptionalFilterQueryParamMatcher(maybeFilter) as _ =>
      handleParticipantsResponse(maybeFilter
        .map(_.getParticipants(Group(group)))
        .getOrElse(participantsCache.getParticipantsAssociatedWithGroup(Group(group)))
      )

    // participants modification endpoints:
    case req@POST -> Root / API_NAME / API_VERSION_1 / "participants" as _ =>
      for {
        participantDtos <- req.req.as[Seq[ParticipantModificationDto]]
        saveResults <- participantDtos.map(participantEventService.createParticipant).sequence
        successfullySaved = parseSuccessfulResults(saveResults)
        response <- Ok(ParticipantsModificationResult(
          message = s"Created successfully ${successfullySaved.size} participants out of ${saveResults.size} requested",
          successfulParticipantIdentifiers = successfullySaved,
          errors = parseErrors(saveResults)
        ))
      } yield response

    case req@PUT -> Root / API_NAME / API_VERSION_1 / "participants" as _ =>
      for {
        participantDtos <- req.req.as[Seq[ParticipantModificationDto]]
        modifyResults <- participantDtos.map(participantEventService.modifyParticipant).sequence
        successfullyModified = parseSuccessfulResults(modifyResults)
        response <- Ok(ParticipantsModificationResult(
          message = s"Modified successfully ${successfullyModified.size} participants out of ${modifyResults.size} requested",
          successfulParticipantIdentifiers = successfullyModified,
          errors = parseErrors(modifyResults)
        ))
      } yield response

    case req@DELETE -> Root / API_NAME / API_VERSION_1 / "participants" as _ =>
      for {
        participantDtos <- req.req.as[Seq[String]]
        removeResults <- participantDtos.map(participantEventService.removeParticipant).sequence
        successfullyRemoved = parseSuccessfulResults(removeResults)
        response <- Ok(ParticipantsModificationResult(
          message = s"Removed successfully ${successfullyRemoved.size} participants out of ${removeResults.size} requested",
          successfulParticipantIdentifiers = successfullyRemoved,
          errors = parseErrors(removeResults)
        ))
      } yield response

  }

  private def handleParticipantsResponse(participants: Task[Seq[Participant]]): Task[Response[Task]] =
    participants.flatMap {
      case participants if participants.nonEmpty => Ok(Seq(participants: _*))
      case _ => Task.eval(Response.notFound)
    }

  private def parseSuccessfulResults(input: Seq[Either[PersistenceError, String]]): List[String] =
    input.filter(_.isRight)
      .map(_.getOrElse(""))
      .filter(_.nonEmpty)
      .toList

  private def parseErrors(input: Seq[Either[PersistenceError, String]]): List[String] =
    input.filter(_.isLeft)
      .map(_.left.map(_.getMessage).left.getOrElse(""))
      .filter(_.nonEmpty)
      .toList

  override def getRoutesWithAuth: AuthedRoutes[User, Task] = routes

  implicit val participantCreationDtoDecoder: EntityDecoder[Task, ParticipantModificationDto] = jsonOf[Task, ParticipantModificationDto]
  implicit val participantCreationDtoSeqDecoder: EntityDecoder[Task, Seq[ParticipantModificationDto]] = jsonOf[Task, Seq[ParticipantModificationDto]]
  implicit val stringSeqEncoder: EntityDecoder[Task, Seq[String]] = jsonOf[Task, Seq[String]]

  implicit val createdParticipantsResultEncoder: EntityEncoder[Task, ParticipantsModificationResult] = jsonEncoderOf[Task, ParticipantsModificationResult]


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
      case _ => ParticipantsFilter.AllParticipantsFilter
    })

  object OptionalFilterQueryParamMatcher extends OptionalQueryParamDecoderMatcher[ParticipantsFilter.ParticipantsFilter]("filter")

}

object ManagementRouting {

  case class ParticipantsModificationResult(message: String, successfulParticipantIdentifiers: List[String], errors: List[String])

}

