package com.github.mattszm.panda.user.token

import cats.data.{EitherT, Kleisli, OptionT}
import com.github.mattszm.panda.user.{User, UserId, UserService}
import monix.eval.Task
import org.http4s.headers.Authorization
import org.http4s.{Credentials, Request}

final class AuthenticatorBasedOnHeader(private val tokenService: TokenService,
                                       private val userService: UserService) extends Authenticator {
  private def retrieveUser: UserId => OptionT[Task, User] =
    id => OptionT(userService.getById(id))

  override def authUser: Kleisli[Task, Request[Task], Either[String, User]] = Kleisli({ request =>
    (
      for {
        header <- EitherT(Task.eval(request.headers.get[Authorization].toRight("Couldn't find an Authorization header")))
        token <- EitherT(tokenService.validateSignedToken(
          header.credentials.asInstanceOf[Credentials.Token].token).map(_.toRight("Invalid token")))
      } yield token).flatMap(retrieveUser(_).toRight("Could not find a user")).value
  })
}
