package com.github.mattszm.panda.participant

import com.github.mattszm.panda.routes.Group
import com.github.mattszm.panda.utils.PersistenceError
import monix.eval.Task

import scala.collection.immutable.MultiDict

final class ParticipantsCacheImpl(private val initParticipants: List[Participant] = List.empty)
  extends ParticipantsCache {
  private var cacheByGroup: MultiDict[Group, Participant] = MultiDict.from(initParticipants.map(p => (p.group, p)))

  override def getParticipantsAssociatedWithGroup(group: Group): Vector[Participant] = cacheByGroup.get(group).toVector

  override def addParticipant(participant: Participant): Task[Either[PersistenceError, Unit]] =
    Task { cacheByGroup = cacheByGroup + ((participant.group, participant)) }.map(Right(_))

  override def addParticipants(participants: List[Participant]): Task[Either[PersistenceError, Unit]] =
    Task {
      cacheByGroup = participants.foldLeft(cacheByGroup)(
      (prev, participant) => prev + ((participant.group, participant)))
    }.map(_ => Right(()))

  override def removeParticipant(participant: Participant): Task[Either[PersistenceError, Unit]] =
    Task { cacheByGroup = cacheByGroup - ((participant.group, participant)) }.map(Right(_))

  override def removeAllParticipantsAssociatedWithGroup(group: Group): Task[Either[PersistenceError, Unit]] =
    Task { cacheByGroup = cacheByGroup -* group }.map(Right(_))
}
