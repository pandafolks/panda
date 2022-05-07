package com.github.mattszm.panda.user
import cats.data.OptionT
import cats.effect.Resource
import cats.implicits.toTraverseOps
import com.github.mattszm.panda.utils.{AlreadyExists, PersistenceError, UnsuccessfulSaveOperation}
import com.mongodb.client.model.Filters
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.bson.BsonInvalidOperationException
import tsec.passwordhashers.jca.BCrypt

import java.util.UUID
import scala.util.Try

final class UserDaoImpl(private val initUsers: List[UserCredentials])(
  private val c: Resource[Task, CollectionOperator[User]]) extends UserDao {
  //todo: split this class into two: dao and service

  locally {
    (for {
      empty <- checkIfEmpty
      _ <- if (empty)
        initUsers
          .map(u => create(u.username, u.password))
          .sequence
      else Task.unit
    } yield ()).onErrorRecover { _ => () }.runAsyncAndForget
  }

  override def byId(id: UserId): Task[Option[User]] =
    c.use {
      case userOperator => userOperator.source.find(Filters.eq(id)).firstOptionL
    }

  override def checkPassword(credentials: UserCredentials): Task[Option[User]] = validateUser(credentials)

  override def delete(credentials: UserCredentials): Task[Boolean] =
    c.use {
      case userOperator => OptionT(validateUser(credentials, userOperator))
        .foldF(Task.now(false))(removeUser(_, userOperator))
  }


  override def create(username: String, password: String): Task[Either[PersistenceError, Unit]] =
    c.use(userOperator =>
      for {
        id <- Task.now(UUID.randomUUID()).map(tagUUIDAsUserId)
        pwd <- BCrypt.hashpw[Task](password)
        exits <- exists(username, userOperator)
        result <- if (!exits)
          userOperator.single.insertOne(User(id, username, pwd))
            .map(_ => Right(()))
            .onErrorRecoverWith {
              case _: BsonInvalidOperationException => Task.now(Right(()))
              case t: Throwable => Task.now(Left(UnsuccessfulSaveOperation(t.getMessage)))
            }
        else Task.now(Left(AlreadyExists("User with the username \"" + username + "\" already exists")))
      } yield result
    )

  private def checkIfEmpty: Task[Boolean] =
    c.use {
      case userOperator => userOperator.source.countAll().map(_ == 0)
    }

  private def exists(username: String, userOperator: CollectionOperator[User]): Task[Boolean] =
    userOperator.source
      .find(Filters.eq("username", username))
      .firstOptionL
      .map(_.isDefined)

  private def validateUser(credentials: UserCredentials, userOperator: CollectionOperator[User]): Task[Option[User]] =
    userOperator.source.find(Filters.eq("username", credentials.username))
        .findL(user => Try(BCrypt.checkpwUnsafe(credentials.password, user.password)).getOrElse(false))

  private def validateUser(credentials: UserCredentials): Task[Option[User]] =
    c.use {
      case userOperator => validateUser(credentials, userOperator)
    }

  private def removeUser(user: User, userOperator: CollectionOperator[User]): Task[Boolean] =
    userOperator.single.deleteOne(Filters.eq(user._id)).map(dr => dr.deleteCount > 0)
}
