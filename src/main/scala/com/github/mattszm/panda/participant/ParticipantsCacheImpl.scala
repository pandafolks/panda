package com.github.mattszm.panda.participant

import com.github.mattszm.panda.routes.Group
import com.github.mattszm.panda.utils.PersistenceError
import monix.eval.Task
import cats.effect.concurrent.Ref

import scala.collection.immutable.MultiDict

final class ParticipantsCacheImpl(private val cacheByGroup: Ref[Task, MultiDict[Group, Participant]])
  extends ParticipantsCache {

  override def getAllGroups: Task[List[Group]] = cacheByGroup.get.map(_.keySet).map(_.toList)

  override def getParticipantsAssociatedWithGroup(group: Group): Task[Vector[Participant]] =
    cacheByGroup.get.map(_.get(group)).map(_.toVector)

  override def addParticipant(participant: Participant): Task[Either[PersistenceError, Unit]] =
    cacheByGroup.update { cacheByGroupBefore => cacheByGroupBefore + ((participant.group, participant)) }.map(Right(_))

  override def addParticipants(participants: List[Participant]): Task[Either[PersistenceError, Unit]] =
    cacheByGroup.update { cacheByGroupBefore => participants.foldLeft(cacheByGroupBefore)(
      (prev, participant) => prev + ((participant.group, participant)))
    }.map(_ => Right(()))

  override def removeParticipant(participant: Participant): Task[Either[PersistenceError, Unit]] =
    cacheByGroup.update { cacheByGroupBefore => cacheByGroupBefore - ((participant.group, participant)) }.map(Right(_))

  override def removeAllParticipantsAssociatedWithGroup(group: Group): Task[Either[PersistenceError, Unit]] =
    cacheByGroup.update { cacheByGroupBefore => cacheByGroupBefore -* group }.map(Right(_))
}

object ParticipantsCacheImpl {
  def apply(initParticipants: List[Participant] = List.empty): Task[ParticipantsCacheImpl] =
    for {
      participantsByGroupRef <- Ref.of[Task, MultiDict[Group, Participant]](
        MultiDict.from(initParticipants.map(p => (p.group, p))))
    } yield new ParticipantsCacheImpl(participantsByGroupRef)
}
