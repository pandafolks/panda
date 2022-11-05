package com.github.pandafolks.panda.user

import com.github.pandafolks.panda.utils.PersistenceError
import monix.eval.Task

trait UserService {

  /** Returns user associated with passed id.
    *
    * @param id
    *   User unique id
    * @return
    *   User object if found one
    */
  def getById(id: UserId): Task[Option[User]]

  /** Validates passed credentials and if validation is passed successfully returns the associated user.
    *
    * @param credentials
    *   Username and password
    * @return
    *   User for requested credentials if found one
    */
  def validateUser(credentials: UserCredentials): Task[Option[User]]

  /** Removes from the persistence layer a user with passed credentials if the one was found.
    *
    * @param credentials
    *   Username and password
    * @return
    *   True if removed successfully, false otherwise
    */
  def delete(credentials: UserCredentials): Task[Boolean]

  /** Creates a user with passed credentials. The limitations arise from the implementation.
    *
    * @param username
    * @param password
    * @return
    *   Either empty if created successfully or PersistenceError if the error during creation occurred
    */
  def create(username: String, password: String): Task[Either[PersistenceError, UserId]]
}
