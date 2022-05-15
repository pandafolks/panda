package com.github.mattszm.panda.user

import com.github.mattszm.panda.utils.PersistenceError
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task

trait UserDao {
  def byId(id: UserId): Task[Option[User]]

  def checkIfEmpty: Task[Boolean]

  def exists(username: String, userOperator: CollectionOperator[User]): Task[Boolean]

  def validateUser(credentials: UserCredentials, userOperator: CollectionOperator[User]): Task[Option[User]]

  def validateUser(credentials: UserCredentials): Task[Option[User]]

  def delete(user: User, userOperator: CollectionOperator[User]): Task[Boolean]

  def insertOne(user: User, userOperator: CollectionOperator[User]): Task[Either[PersistenceError, Unit]]
}
