package com.github.mattszm.panda.management

import com.github.mattszm.panda.management.SubRouting.{API_NAME, API_VERSION_1}
import com.github.mattszm.panda.participant.{Participant, ParticipantsCache}
import com.github.mattszm.panda.routes.Group
import com.github.mattszm.panda.user.User
import monix.eval.Task
import org.http4s.{AuthedRoutes, Response}
import org.http4s.dsl.Http4sDsl

final class ManagementRouting(private val participantsCache: ParticipantsCache) extends Http4sDsl[Task]
  with SubRoutingWithAuth {

  private val routes = AuthedRoutes.of[User, Task] {
    case req@POST -> Root / API_NAME / API_VERSION_1 / "participants" as _ =>
      for {
        participants <- req.req.as[Seq[Participant]]
        addResult <- participantsCache.addParticipants(participants.toList)
        response <- addResult.fold(_ => BadRequest("Participants not saved"), _ => Ok())
      } yield response

    case _@GET -> Root / API_NAME / API_VERSION_1 / "participants" / group as _ =>
      participantsCache.getParticipantsAssociatedWithGroup(Group(group)).flatMap {
        case participants if participants.nonEmpty => Ok(Seq(participants: _*))
        case _ => Task.eval(Response.notFound)
      }
  }

  override def getRoutes: AuthedRoutes[User, Task] = routes
}
