package com.github.mattszm.panda.user

import cats.data.{Kleisli, OptionT}
import fs2.Stream
import fs2.text.utf8Encode
import monix.eval.Task
import org.http4s.Status.Forbidden
import org.http4s.{AuthedRoutes, Request, Response}

trait Authenticator {
  def authUser: Kleisli[Task, Request[Task], Either[String, User]]

  def onFailure: AuthedRoutes[String, Task] =
    Kleisli(req => OptionT.liftF(Task.now(
      Response[Task](
        status = Forbidden,
        body = Stream(req.context).through(utf8Encode)
      )
    )))
}
