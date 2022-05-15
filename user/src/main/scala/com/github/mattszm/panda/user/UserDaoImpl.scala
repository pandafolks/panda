package com.github.mattszm.panda.user
import cats.effect.Resource
import com.github.mattszm.panda.utils.{PersistenceError, UnsuccessfulSaveOperation}
import com.mongodb.client.model.Filters
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task
import org.bson.BsonInvalidOperationException
import tsec.passwordhashers.jca.BCrypt

import scala.util.Try

final class UserDaoImpl(private val c: Resource[Task, CollectionOperator[User]]) extends UserDao {

  override def byId(id: UserId): Task[Option[User]] =
    c.use(userOperator => userOperator.source.find(Filters.eq(id)).firstOptionL)

  override def checkIfEmpty: Task[Boolean] =
    c.use(userOperator => userOperator.source.countAll().map(_ == 0))

  override def exists(username: String, userOperator: CollectionOperator[User]): Task[Boolean] =
    userOperator.source
      .find(Filters.eq("username", username))
      .firstOptionL
      .map(_.isDefined)

  override def validateUser(credentials: UserCredentials, userOperator: CollectionOperator[User]): Task[Option[User]] =
    userOperator.source.find(Filters.eq("username", credentials.username))
        .findL(user => Try(BCrypt.checkpwUnsafe(credentials.password, user.password)).getOrElse(false))

  override def validateUser(credentials: UserCredentials): Task[Option[User]] =
    c.use(userOperator => validateUser(credentials, userOperator))

  override def delete(user: User, userOperator: CollectionOperator[User]): Task[Boolean] =
    userOperator.single.deleteOne(Filters.eq(user._id)).map(dr => dr.deleteCount > 0)

  override def insertOne(user: User, userOperator: CollectionOperator[User]): Task[Either[PersistenceError, Unit]] =
    userOperator.single.insertOne(user)
      .map(_ => Right(()))
      .onErrorRecoverWith {
        case _: BsonInvalidOperationException => Task.now(Right(()))
        case t: Throwable => Task.now(Left(UnsuccessfulSaveOperation(t.getMessage)))
      }
}
