package com.github.mattszm.panda.user

import com.github.mattszm.panda.utils.PersistenceError
import monix.eval.Task

trait UserDao {
  def byId(id: UserId): Task[Option[User]]

  def checkPassword(credentials: UserCredentials): Task[Option[User]]

  def delete(credentials: UserCredentials): Task[Boolean]

  def create(username: String, password: String): Task[Either[PersistenceError, Unit]]
}
