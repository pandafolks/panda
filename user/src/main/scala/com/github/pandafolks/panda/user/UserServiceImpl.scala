package com.github.pandafolks.panda.user

import cats.data.OptionT
import cats.effect.Resource
import cats.implicits.toTraverseOps
import com.github.pandafolks.panda.user.token.Token
import com.github.pandafolks.panda.utils.{AlreadyExists, PandaStartupException, PersistenceError, UndefinedPersistenceError}
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.slf4j.LoggerFactory
import tsec.passwordhashers.jca.BCrypt

import java.util.UUID
import scala.concurrent.duration.DurationInt

final class UserServiceImpl(private val userDao: UserDao, private val initUsers: List[UserCredentials] = List.empty)(
  private val c: Resource[Task, (CollectionOperator[User], CollectionOperator[Token])]) extends UserService {

  private val logger = LoggerFactory.getLogger(getClass.getName)

  locally {
    // Inserting init user if there are no other users in the persistence layer (on startup).
    (
      for {
        empty <- userDao.checkIfEmpty
        res <- if (empty)
          initUsers
            .map(u => create(u.username, u.password))
            .sequence
            .map(results =>
              if (results.exists(_.isLeft)) Left(UndefinedPersistenceError("Could not create init users"))
              else Right(())
            )
        else Task.now(Right(()))
      } yield res
      )
      .onErrorRecoverWith { e => Task.now(Left(UndefinedPersistenceError(e.getMessage))) }
      .runSyncUnsafe(10.seconds)
      .fold(persistenceError => {
        logger.error("The error occurred during init user creation.")
        throw new PandaStartupException(persistenceError.getMessage)
      }, _ => ())
  }

  override def getById(id: UserId): Task[Option[User]] =
    userDao.byId(id).onErrorRecoverWith(_ => Task.now(Option.empty))

  override def validateUser(credentials: UserCredentials): Task[Option[User]] =
    userDao.validateUser(credentials).onErrorRecoverWith(_ => Task.now(Option.empty))

  override def delete(credentials: UserCredentials): Task[Boolean] =
    c.use {
      case (userOperator, _) =>
        OptionT(userDao.validateUser(credentials, userOperator))
          .foldF(Task.now(false))(userDao.delete(_, userOperator))
    }.onErrorRecoverWith { _ => Task.now(false) }

  override def create(username: String, password: String): Task[Either[PersistenceError, UserId]] =
    c.use {
      case (userOperator, _) =>
        for {
          id <- Task.now(UUID.randomUUID()).map(tagUUIDAsUserId)
          pwd <- BCrypt.hashpw[Task](password)
          exits <- userDao.exists(username, userOperator)
          result <- if (!exits)
            userDao.insertOne(User(id, username, pwd), userOperator)
          else
            Task.now(Left(AlreadyExists("User with the username \"" + username + "\" already exists")))
        } yield result.map(_ => id)
    }.onErrorRecoverWith { e => Task.now(Left(UndefinedPersistenceError(e.getMessage))) }
}
