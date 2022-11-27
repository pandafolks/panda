package com.github.pandafolks.panda.healthcheck

import com.github.pandafolks.panda.utils.PersistenceError
import monix.eval.Task

trait UnsuccessfulHealthCheckDao {

  /** Increment the counter associated with the requested identifier.
    *
    * @param identifier
    *   Unique identifier
    * @return
    *   Either a counter after incrementation or [[PersistenceError]] if the error occurred
    */
  def incrementCounter(identifier: String): Task[Either[PersistenceError, Long]]

  /** Removes an entry from a collection with the requested identifier.
    *
    * @param identifier
    *   Unique identifier
    * @return
    *   Either empty if successful or [[PersistenceError]] if the error occurred
    */
  def clear(identifier: String): Task[Either[PersistenceError, Unit]]

  /** Marks elements with associated identifiers as turned off.
    *
    * @param identifiers
    *   A list of unique identifiers
    * @return
    *   Either empty if successful or [[PersistenceError]] if the error occurred
    */
  def markAsTurnedOff(identifiers: List[String]): Task[Either[PersistenceError, Unit]]
}
