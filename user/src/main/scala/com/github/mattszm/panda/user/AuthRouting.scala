package com.github.mattszm.panda.user

import com.github.mattszm.panda.user.token.TokenService
import com.github.mattszm.panda.utils.SubRouting._
import com.github.mattszm.panda.utils.{AlreadyExists, SubRoutingWithNoAuth}
import monix.eval.Task
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpRoutes, Response, Status}

final class AuthRouting(private val tokenService: TokenService, private val userService: UserService
                       ) extends Http4sDsl[Task] with SubRoutingWithNoAuth {
  private val AUTH_NAME: String = "auth"

  private val routes = HttpRoutes.of[Task] {
    case req@POST -> Root / API_NAME / API_VERSION_1 / AUTH_NAME / "login" =>
      (
        for {
          user <- req.as[UserCredentials]
          userOpt <- userService.checkPassword(user)
        } yield userOpt
        ).flatMap {
        case Some(user) => Ok(tokenService.signToken(user))
        case None => Task.now(Response[Task](Status.Unauthorized))
      }

    case req@POST -> Root / API_NAME / API_VERSION_1 / AUTH_NAME / "register" =>
      (
        for {
          user <- req.as[UserCredentials]
          res <- userService.create(user.username, user.password)
        } yield res
        ).flatMap {
        case Right(_) => Task.now(Response[Task](Status.Created))
        case Left(AlreadyExists(message)) => Conflict(message)
        case _ => Task.now(Response[Task](Status.BadRequest))
      }

    case req@DELETE -> Root / API_NAME / API_VERSION_1 / AUTH_NAME / "destroy" =>
      for {
        user <- req.as[UserCredentials]
        result <- userService.delete(user)
      } yield Response[Task](if (result) Status.NoContent else Status.Unauthorized)
  }

  override def getRoutes: HttpRoutes[Task] = routes
}