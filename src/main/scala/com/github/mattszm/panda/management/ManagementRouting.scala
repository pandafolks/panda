package com.github.mattszm.panda.management

import cats.implicits.toTraverseOps
import com.github.mattszm.panda.management.SubRouting.{API_NAME, API_VERSION_1}
import com.github.mattszm.panda.participant.dto.ParticipantCreationDto
import com.github.mattszm.panda.participant.event.ParticipantEventService
import com.github.mattszm.panda.participant.{Participant, ParticipantsCache}
import com.github.mattszm.panda.routes.Group
import com.github.mattszm.panda.user.User
import io.circe.generic.auto._
import monix.eval.Task
import org.http4s.circe.jsonOf
import org.http4s.dsl.Http4sDsl
import org.http4s.{AuthedRoutes, EntityDecoder, Response}


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
        // todo mszmal: start here!
        participantDtos <- req.req.as[Seq[ParticipantCreationDto]]
        _ <- Task.now(participantDtos).foreachL(e => println(e))
        response <- Ok()
      } yield response

    case _@GET -> Root / API_NAME / API_VERSION_1 / "participants" / group as _ =>
      handleParticipantsResponse(participantsCache.getParticipantsAssociatedWithGroup(Group(group)))
  }

  private def handleParticipantsResponse(participants: Task[Seq[Participant]]): Task[Response[Task]] =
    participants.flatMap {
      case participants if participants.nonEmpty => Ok(Seq(participants: _*))
      case _ => Task.eval(Response.notFound)
    }

  override def getRoutes: AuthedRoutes[User, Task] = routes

  implicit val participantCreationDtoDecoder: EntityDecoder[Task, ParticipantCreationDto] = jsonOf[Task, ParticipantCreationDto]
  implicit val participantCreationDtoSeqDecoder: EntityDecoder[Task, Seq[ParticipantCreationDto]] = jsonOf[Task, Seq[ParticipantCreationDto]]
}
