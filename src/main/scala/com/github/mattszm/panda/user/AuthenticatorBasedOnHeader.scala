package com.github.mattszm.panda.user

import cats.data.{Kleisli, OptionT}
import cats.implicits._
import io.chrisdavenport.fuuid.FUUID
import monix.eval.Task
import org.http4s.{Credentials, Request}
import org.http4s.headers.Authorization

final class AuthenticatorBasedOnHeader(identityStore: UserId => OptionT[Task, User]) extends Authenticator {
  // in the final impl we would take it from db
  private def retrieveUser: String => OptionT[Task, User] =
    id => OptionT(Task.eval(FUUID.fromStringOpt(id)))
      .map(tagFUUIDAsUserId)
      .flatMap(userId => identityStore(userId))

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
