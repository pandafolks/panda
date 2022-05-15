package com.github.mattszm.panda.management

import cats.implicits.toTraverseOps
import com.github.mattszm.panda.management.ManagementRouting.ParticipantsModificationResult
import com.github.mattszm.panda.participant.dto.ParticipantModificationDto
import com.github.mattszm.panda.participant.event.ParticipantEventService
import com.github.mattszm.panda.participant.{Participant, ParticipantsCache}
import com.github.mattszm.panda.routes.Group
import com.github.mattszm.panda.user.{SubRoutingWithAuth, User}
import com.github.mattszm.panda.utils.PersistenceError
import com.github.mattszm.panda.utils.SubRouting._
import io.circe.generic.auto._
import monix.eval.Task
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.dsl.Http4sDsl
import org.http4s.{AuthedRoutes, EntityDecoder, EntityEncoder, Response}


final class ManagementRouting(private val participantEventService: ParticipantEventService,
                              private val participantsCache: ParticipantsCache) extends Http4sDsl[Task]
  with SubRoutingWithAuth {

  private val routes = AuthedRoutes.of[User, Task] {
    case _@GET -> Root / API_NAME / API_VERSION_1 / "groups" as _ =>
      participantsCache.getAllGroups.flatMap {
        case groups if groups.nonEmpty => Ok(Seq(groups: _*))
        case _ => Task.eval(Response.notFound)
      }

    case _@GET -> Root / API_NAME / API_VERSION_1 / "participants" as _ =>
      handleParticipantsResponse(
        participantsCache.getAllGroups.
          flatMap(groups => groups.map(group => participantsCache.getParticipantsAssociatedWithGroup(group)).sequence)
          .map(_.flatten)
      )

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

    case _@GET -> Root / API_NAME / API_VERSION_1 / "participants" / group as _ =>
      handleParticipantsResponse(participantsCache.getParticipantsAssociatedWithGroup(Group(group)))
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

  override def getRoutes: AuthedRoutes[User, Task] = routes

  implicit val participantCreationDtoDecoder: EntityDecoder[Task, ParticipantModificationDto] = jsonOf[Task, ParticipantModificationDto]
  implicit val participantCreationDtoSeqDecoder: EntityDecoder[Task, Seq[ParticipantModificationDto]] = jsonOf[Task, Seq[ParticipantModificationDto]]
  implicit val stringSeqEncoder: EntityDecoder[Task, Seq[String]] = jsonOf[Task, Seq[String]]

  implicit val createdParticipantsResultEncoder: EntityEncoder[Task, ParticipantsModificationResult] = jsonEncoderOf[Task, ParticipantsModificationResult]
}

object ManagementRouting {
  case class ParticipantsModificationResult(message: String, successfulParticipantIdentifiers: List[String], errors: List[String])
}
