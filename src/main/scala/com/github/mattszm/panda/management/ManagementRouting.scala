package com.github.mattszm.panda.management

import com.github.mattszm.panda.management.SubRouting.{API_NAME, API_VERSION_1}
import com.github.mattszm.panda.participant.{Participant, ParticipantsCache}
import monix.eval.Task
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

final class ManagementRouting(private val participantsCache: ParticipantsCache) extends Http4sDsl[Task] with SubRouting {
  private val routes = HttpRoutes.of[Task] {
    case req@POST -> Root / API_NAME / API_VERSION_1 / "participants" =>
      for {
        participants <- req.as[Seq[Participant]]
        addResult <- participantsCache.addParticipants(participants.toList)
        response <- addResult.fold(_ => BadRequest("Participants not saved"), _ => Ok())
      } yield response
  }

  override def getRoutes: HttpRoutes[Task] = routes
}
