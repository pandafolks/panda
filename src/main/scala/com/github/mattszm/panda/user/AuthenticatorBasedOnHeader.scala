package com.github.mattszm.panda.user

import cats.data.{Kleisli, OptionT}
import cats.implicits._
import monix.eval.Task
import org.http4s.{Credentials, Request}
import org.http4s.headers.Authorization

import java.util.UUID
import scala.util.Try

final class AuthenticatorBasedOnHeader(private val userService: UserService) extends Authenticator {
  private def retrieveUser: String => OptionT[Task, User] =
    id => OptionT(Task.eval(Try(UUID.fromString(id)).toOption))
      .map(tagUUIDAsUserId)
      .flatMap(userId => OptionT(userService.getById(userId)))

  override def authUser: Kleisli[Task, Request[Task], Either[String, User]] = Kleisli({ request =>
    (
      for {
        header <- request.headers.get[Authorization].toRight("Couldn't find an Authorization header")
        token <- TokenService.validateSignedToken(
          header.credentials.asInstanceOf[Credentials.Token].token).toRight("Invalid token")
      } yield token
      ).traverse(retrieveUser).getOrElse(Either.left("Could not find a user"))
  })
}
