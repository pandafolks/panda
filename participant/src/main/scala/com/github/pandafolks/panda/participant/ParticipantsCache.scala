package com.github.pandafolks.panda.participant

import com.github.pandafolks.panda.routes.Group
import com.github.pandafolks.panda.utils.ChangeListener
import monix.eval.Task

trait ParticipantsCache {
  /**
   * Returns all groups present inside the cache
   *
   * @return            List of groups
   */
  def getAllGroups: Task[List[Group]]

  /**
   * Returns all participants present inside the cache
   *
   * @return            List of participants
   */
  def getAllParticipants: Task[List[Participant]]

  /**
   * Returns all participants present inside the cache with `Working` status
   *
   * @return            List of working participants
   */
  def getAllWorkingParticipants: Task[List[Participant]]

  /**
   * Returns all participants present inside the cache with both `Working` status and `Healthy` health state
   *
   * @return            List of working and healthy participants
   */
  def getAllHealthyParticipants: Task[List[Participant]]

  /**
   * Returns all participants present inside the cache for the requested group despite what state/health it has
   *
   * @param group       Requested group
   *
   * @return            Vector of participants associated with the group or empty vector if there are no such participants
   */
  def getParticipantsAssociatedWithGroup(group: Group): Task[Vector[Participant]]

  /**
   * Returns all participants present inside the cache with `Working` status for the requested group
   *
   * @param group       Requested group
   *
   * @return            Vector of participants associated with the group or empty vector if there are no such participants
   */
  def getWorkingParticipantsAssociatedWithGroup(group: Group): Task[Vector[Participant]]

  /**
   * Returns all participants present inside the cache with both `Working` status and `Healthy` health state
   * for the requested group
   *
   * @param group       Requested group
   *
   * @return            Vector of participants associated with the group or empty vector if there are no such participants
   */
  def getHealthyParticipantsAssociatedWithGroup(group: Group): Task[Vector[Participant]]

  /**
   * Register listener which will be notified about all participants taken from the persistence layer and participants
   * that were present in cache but are no more. Listeners are invoked on every cache refresh.

   *
   * @param listener    [[ChangeListener]] that expects participants
   *
   * @return
   */
  def registerListener(listener: ChangeListener[Participant]): Task[Unit]
}
