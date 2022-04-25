package com.github.mattszm.panda.user

import com.github.mattszm.panda.management.SubRouting.{API_NAME, API_VERSION_1}
import com.github.mattszm.panda.management.SubRoutingWithNoAuth
import monix.eval.Task
import org.http4s.{HttpRoutes, Response, Status}
import org.http4s.dsl.Http4sDsl

final class AuthRouting(
                         checkPassword: UserCredentials => Task[Option[User]]
                       ) extends Http4sDsl[Task] with SubRoutingWithNoAuth {
  private val AUTH_NAME: String = "auth"

  private val routes = HttpRoutes.of[Task] {
    case req@POST -> Root / API_NAME / API_VERSION_1 / AUTH_NAME / "login" =>
      (
        for {
          user <- req.as[UserCredentials]
          userOpt <- checkPassword(user)
        } yield userOpt
        ).flatMap {
        case Some(user) => Ok(TokenService.signToken(user))
        case None => Task.now(Response[Task](Status.Unauthorized))
      }

    case _@POST -> Root / API_NAME / API_VERSION_1 / AUTH_NAME / "register" => ???

    case _@DELETE -> Root / API_NAME / API_VERSION_1 / AUTH_NAME / "destroy" => ???
  }

  override def getRoutes: HttpRoutes[Task] = routes
}
