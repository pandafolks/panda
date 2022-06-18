package com.github.pandafolks.panda.user.token

import cats.data.{EitherT, Kleisli, OptionT}
import com.github.pandafolks.panda.utils.cache.{CustomCache, CustomCacheImpl}
import com.github.pandafolks.panda.user.{User, UserId, UserService}
import monix.eval.Task
import org.http4s.headers.Authorization
import org.http4s.{Credentials, Request}

import scala.concurrent.duration.DurationInt

final class AuthenticatorBasedOnHeader(private val tokenService: TokenService, private val userService: UserService)(
  private val cacheTtl: Int) extends Authenticator {

  private val cache: CustomCache[UserId, Option[User]] = new CustomCacheImpl[UserId, Option[User]](
    id => userService.getById(id)
  )(maximumSize = 50L, ttl = cacheTtl.seconds)

  override def authUser: Kleisli[Task, Request[Task], Either[String, User]] = Kleisli({ request =>
    (
      for {
        header <- EitherT(Task.eval(request.headers.get[Authorization].toRight("Couldn't find an Authorization header")))
        token <- EitherT(tokenService.validateSignedToken(
          header.credentials.asInstanceOf[Credentials.Token].token).map(_.toRight("Invalid token")))
      } yield token).flatMap(userId => OptionT(cache.get(userId)).toRight("Could not find a user")).value
  })
}
