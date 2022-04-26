package com.github.mattszm.panda.management

import cats.implicits.toTraverseOps
import com.datastax.oss.driver.api.core.CqlSession
import com.github.mattszm.panda.db.{CqlStrings, Executable}
import com.github.mattszm.panda.management.SubRouting.{API_NAME, API_VERSION_1}
import com.github.mattszm.panda.participant.{Participant, ParticipantsCache}
import com.github.mattszm.panda.routes.Group
import com.github.mattszm.panda.user.User
import monix.eval.Task
import org.http4s.dsl.Http4sDsl
import org.http4s.{AuthedRoutes, Response}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import monix.execution.Scheduler.Implicits.global


final class ManagementRouting(private val participantsCache: ParticipantsCache, implicit val session: CqlSession) extends Http4sDsl[Task]
  with SubRoutingWithAuth {

  private val routes = AuthedRoutes.of[User, Task] {
    case req@POST -> Root / API_NAME / API_VERSION_1 / "participants" as _ =>
      for {
        participants <- req.req.as[Seq[Participant]].map(
          _.map(p => if (p.identifier.isBlank) Participant(p.host, p.port, p.group) else p))
        addResult <- participantsCache.addParticipants(participants.toList)
        response <- addResult.fold(_ => BadRequest("Participants not saved"), _ => Ok())
      } yield response

    case _@GET -> Root / API_NAME / API_VERSION_1 / "groups" as _ =>
      participantsCache.getAllGroups.flatMap {
        case groups if groups.nonEmpty => Ok(Seq(groups: _*))
        case _ => Task.eval(Response.notFound)
      }

    case _@GET -> Root / API_NAME / API_VERSION_1 / "participants" as _ =>
      //todo mszmal: delete!
      val statement = Executable.query(cql"SELECT * FROM system_schema.keyspaces")
      Await.result(statement.foreach(l => print(l.getColumnDefinitions)), 2.minutes)
      handleParticipantsResponse(
        participantsCache.getAllGroups.
          flatMap(groups => groups.map(group => participantsCache.getParticipantsAssociatedWithGroup(group)).sequence)
          .map(_.flatten)
      )

    case _@GET -> Root / API_NAME / API_VERSION_1 / "participants" / group as _ =>
      handleParticipantsResponse(participantsCache.getParticipantsAssociatedWithGroup(Group(group)))
  }

  private def handleParticipantsResponse(participants: Task[Seq[Participant]]): Task[Response[Task]] =
    participants.flatMap {
      case participants if participants.nonEmpty => Ok(Seq(participants: _*))
      case _ => Task.eval(Response.notFound)
    }

  override def getRoutes: AuthedRoutes[User, Task] = routes
}
