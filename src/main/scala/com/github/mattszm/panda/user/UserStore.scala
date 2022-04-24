package com.github.mattszm.panda.user

import cats.Applicative
import cats.data.OptionT
import cats.effect.concurrent.Ref
import cats.implicits.{catsSyntaxFoldOps, toTraverseOps}
import io.chrisdavenport.fuuid.FUUID
import monix.eval.Task
import tsec.passwordhashers.jca.BCrypt

final case class UserStore(
                      identityStore: UserId => OptionT[Task, User],
                      checkPassword: UserCredentials => Task[Option[User]]
                    )

object UserStore {
  def newUser(username: String, password: String): Task[User] =
    Applicative[Task].map2(
      FUUID.randomFUUID[Task].map(tagFUUIDAsUserId),
      BCrypt.hashpw[Task](password)
    )((id, password) => User(id, username, password))

  private def validateUser(credentials: UserCredentials)(users: List[User]): Task[Option[User]] =
    users.findM(
      user =>
        BCrypt
          .checkpwBool[Task](credentials.password, user.password)
          .map(passwordsMatch => passwordsMatch && credentials.username == user.username)
    )

  def apply(users: List[UserCredentials]): Task[UserStore] =
    for {
      userList <- users
        .map(u => UserStore.newUser(u.username, u.password))
        .sequence
      usersRef <- Ref.of[Task, Map[UserId, User]](
        userList.foldLeft(Map.empty[UserId, User])((prevMap, user) => prevMap + (user.id -> user)))
    } yield new UserStore(
        (id: UserId) => OptionT(usersRef.get.map(_.get(id))),
        requestedUser => usersRef.get.map(_.values.toList).flatMap(users => validateUser(requestedUser)(users))
      )
}