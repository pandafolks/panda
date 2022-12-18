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

  /** Removes entries from a collection with the requested identifiers.
    *
    * @param identifiers
    *   Unique identifiers
    * @return
    *   Either empty if successful or [[PersistenceError]] if the error occurred
    */
  def clear(identifiers: List[String]): Task[Either[PersistenceError, Unit]]

  /** Marks elements with associated identifiers as turned off.
    *
    * @param identifiers
    *   A list of unique identifiers
    * @return
    *   Either empty if successful or [[PersistenceError]] if the error occurred
    */
  def markAsTurnedOff(identifiers: List[String]): Task[Either[PersistenceError, Unit]]

  /** Get [[UnsuccessfulHealthCheck]] entries that are stale. Stale recognition is based on the last update timestamp
    * and counter. There need to be entries with the last update timestamp smaller or equal to the current Time -
    * deviation. The counter needs to reach at least the minimum failed counter.
    *
    * @param deviation
    *   deviation expressed in milliseconds based on which the filtration of stale [[UnsuccessfulHealthCheck]]s is
    *   carried out.
    * @param minimumFailedCounters
    *   specified the minimum value of the counters we are looking for
    *
    * @return
    *   List of the [[UnsuccessfulHealthCheck]] that meet the filtering criteria.
    */
  def getStaleEntries(deviation: Long, minimumFailedCounters: Int): Task[List[UnsuccessfulHealthCheck]]
}
