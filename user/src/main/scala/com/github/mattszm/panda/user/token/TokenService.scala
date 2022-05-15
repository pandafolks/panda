package com.github.mattszm.panda.user.token

import com.github.mattszm.panda.user.{User, UserId}
import monix.eval.Task

trait TokenService {

  def signToken(user: User): Task[String]

  def validateSignedToken(token: String): Task[Option[UserId]]
}
