package com.github.pandafolks.panda.user.token

import com.github.pandafolks.panda.user.{User, UserId}
import monix.eval.Task

trait TokenService {

  /** Creates a token for the passed user.
    *
    * @param user
    *   User object for which the token should be generated
    * @return
    *   Token represented as a string
    */
  def signToken(user: User): Task[String]

  /** Validates if the passed token is valid and if yes returns the user associated with the token. The validation may
    * depend on various criteria that are specific for the particular implementation.
    *
    * @param token
    *   Token represented as a string
    * @return
    *   Either UserId if the user was successfully associated with the token, or empty if the token is not valid
    */
  def validateSignedToken(token: String): Task[Option[UserId]]
}
