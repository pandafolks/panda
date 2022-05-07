package com.github.mattszm.panda.user

import cats.data.OptionT
import cats.effect.Resource
import cats.implicits.toTraverseOps
import com.mongodb.client.model.Filters
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task
import org.bson.BsonInvalidOperationException
import tsec.passwordhashers.jca.BCrypt

import java.util.UUID
import scala.util.Try

final case class UserStore(
                            identityStore: UserId => OptionT[Task, User],
                            checkPassword: UserCredentials => Task[Option[User]],
                            delete: UserCredentials => Task[Boolean]
                          )

object UserStore {
  def newUser(username: String, password: String)(c: Resource[Task, CollectionOperator[User]]): Task[Unit] =
    for {
      id <- Task.now(UUID.randomUUID()).map(tagUUIDAsUserId)
      pwd <- BCrypt.hashpw[Task](password)
      _ <- c.use(userOperator => userOperator.single.insertOne(User(id, username, pwd)))
        .onErrorRecoverWith { case _: BsonInvalidOperationException => Task.unit }
    } yield ()

  private def validateUser(credentials: UserCredentials)(c: Resource[Task, CollectionOperator[User]]): Task[Option[User]] =
    c.use {
      case userOperator => userOperator.source.find(Filters.eq("username", credentials.username))
        .findL(user => Try(BCrypt.checkpwUnsafe(credentials.password, user.password)).getOrElse(false))
    }

  private def removeUser(user: User)(c: Resource[Task, CollectionOperator[User]]): Task[Boolean] =
    c.use {
      case userOperator => userOperator.single.deleteOne(Filters.eq(user._id)).map(dr => dr.deleteCount > 0)
    }

  private def checkIfEmpty(c: Resource[Task, CollectionOperator[User]]): Task[Boolean] =
    c.use {
      case userOperator => userOperator.source.countAll().map(_ == 0)
    }

  def apply(users: List[UserCredentials])(c: Resource[Task, CollectionOperator[User]]): Task[UserStore] =
    for {
      empty <- checkIfEmpty(c)
      _ <- if (empty)
        users
          .map(u => UserStore.newUser(u.username, u.password)(c))
          .sequence
      else Task.unit
    } yield new UserStore(
        (id: UserId) => OptionT(c.use {
          case userOperator => userOperator.source.find(Filters.eq(id)).firstOptionL
        }),
        requestedUser => validateUser(requestedUser)(c),
        requestedUser => OptionT(validateUser(requestedUser)(c)).foldF(Task.now(false))(removeUser(_)(c))
    )
}