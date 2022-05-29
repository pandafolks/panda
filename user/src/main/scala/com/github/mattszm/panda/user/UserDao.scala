package com.github.mattszm.panda.user

import com.github.mattszm.panda.utils.PersistenceError
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task

trait UserDao {
  /**
   * Returns user associated with passed id.
   *
   * @param id             User unique id
   * @return               User object if found one
   */
  def byId(id: UserId): Task[Option[User]]

  /**
   * Checks whether there is any user in a database.
   *
   * @return               True if exists, false otherwise
   */
  def checkIfEmpty: Task[Boolean]

  /**
   * Checks whether a user with a passed username exists.
   *
   * @param username       Username
   * @param userOperator   User DB entry point
   * @return               True if exists, false otherwise
   */
  def exists(username: String, userOperator: CollectionOperator[User]): Task[Boolean]

  /**
   * Validates passed credentials and if validation is passed successfully returns the associated user.
   *
   * @param credentials    Username and password
   * @param userOperator   User DB entry point
   * @return               User for requested credentials if found one
   */
  def validateUser(credentials: UserCredentials, userOperator: CollectionOperator[User]): Task[Option[User]]

  /**
   * Validates passed credentials and if validation is passed successfully returns the associated user.
   *
   * @param credentials    Username and password
   * @return               User for requested credentials if found one
   */
  def validateUser(credentials: UserCredentials): Task[Option[User]]

  /**
   * Removes a passed user from the persistence layer.
   *
   * @param user           User to delete
   * @param userOperator   User DB entry point
   * @return               True if removed successfully, false otherwise
   */
  def delete(user: User, userOperator: CollectionOperator[User]): Task[Boolean]

  /**
   * Inserts a passed user to the persistence layer.
   *
   * @param user           User to insert
   * @param userOperator   User DB entry point
   * @return               Either empty if inserted successfully or PersistenceError if the error during inserting occurred
   */
  def insertOne(user: User, userOperator: CollectionOperator[User]): Task[Either[PersistenceError, Unit]]
}
