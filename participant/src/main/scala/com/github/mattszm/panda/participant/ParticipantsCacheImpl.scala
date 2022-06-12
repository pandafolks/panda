package com.github.mattszm.panda.participant

import cats.effect.concurrent.Ref
import cats.implicits.toTraverseOps
import com.github.mattszm.panda.participant.event.ParticipantEventService
import com.github.mattszm.panda.routes.Group
import com.github.mattszm.panda.utils.{ChangeListener, DefaultPublisher}
import monix.eval.Task

import scala.collection.immutable.MultiDict

final class ParticipantsCacheImpl(private val participantEventService: ParticipantEventService,
                                  private val cacheRefreshInterval: Int)(
  private val cacheByGroup: Ref[Task, MultiDict[Group, Participant]]) extends ParticipantsCache {

  private val publisher: DefaultPublisher[Participant] = new DefaultPublisher[Participant]()

  override def getAllGroups: Task[List[Group]] = cacheByGroup.get.map(_.keySet).map(_.toList)

  override def getParticipantsAssociatedWithGroup(group: Group): Task[Vector[Participant]] =
    cacheByGroup.get.map(_.get(group)).map(_.toVector)

  override def getWorkingParticipantsAssociatedWithGroup(group: Group): Task[Vector[Participant]] =
    getParticipantsAssociatedWithGroup(group).map(_.filter(_.status == Working))

  override def addParticipant(participant: Participant): Task[Unit] =
    Task.parZip2(
      cacheByGroup.update { cacheByGroupBefore => cacheByGroupBefore + ((participant.group, participant)) },
      publisher.getListeners.flatMap(_.map(_.notifyAboutAdd(List(participant))).sequence)
    ).void

  override def addParticipants(participants: List[Participant]): Task[Unit] =
    Task.parZip2(
      cacheByGroup.update { cacheByGroupBefore =>
        participants.foldLeft(cacheByGroupBefore)(
          (prev, participant) => prev + ((participant.group, participant)))
      },
      publisher.getListeners.flatMap(_.map(_.notifyAboutAdd(participants)).sequence)
    ).void

  override def removeParticipant(participant: Participant): Task[Unit] =
    Task.parZip2(
      cacheByGroup.update { cacheByGroupBefore => cacheByGroupBefore - ((participant.group, participant)) },
      publisher.getListeners.flatMap(_.map(_.notifyAboutRemove(List(participant))).sequence)
    ).void

  override def removeAllParticipantsAssociatedWithGroup(group: Group): Task[Unit] =
    cacheByGroup.getAndUpdate { cacheByGroupBefore => cacheByGroupBefore -* group }
      .flatMap(previousCacheState => publisher.getListeners
        .flatMap(_.map(_.notifyAboutRemove(previousCacheState.get(group).toList)).sequence)
      ).void

  override def registerListener(listener: ChangeListener[Participant]): Task[Unit] =
    for {
      _ <- publisher.register(listener)
      cache <- cacheByGroup.get
      _ <- listener.notifyAboutAdd(cache.values.toList) // initial load to the listener
    } yield ()
}

object ParticipantsCacheImpl {
  def apply(participantEventService: ParticipantEventService,
            initParticipants: List[Participant] = List.empty,
            cacheRefreshInterval: Int = Int.MaxValue): Task[ParticipantsCacheImpl] =
    for {
      participantsByGroupRef <- Ref.of[Task, MultiDict[Group, Participant]](
        MultiDict.from(initParticipants.map(p => (p.group, p))))
    } yield new ParticipantsCacheImpl(participantEventService, cacheRefreshInterval)(participantsByGroupRef)
}
