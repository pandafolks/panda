package com.github.pandafolks.panda.participant

import cats.effect.concurrent.Ref
import cats.implicits.toTraverseOps
import com.github.pandafolks.panda.participant.event.ParticipantEventService
import com.github.pandafolks.panda.routes.Group
import com.github.pandafolks.panda.utils.{ChangeListener, DefaultPublisher}
import monix.eval.Task
import org.slf4j.LoggerFactory

import scala.collection.immutable.MultiDict
import scala.concurrent.duration.DurationInt

final class ParticipantsCacheImpl private( // This constructor cannot be used directly
                                           private val participantEventService: ParticipantEventService,
                                           private val cacheRefreshInterval: Int
                                         )(
                                           // It is much more efficient to keep multiple cache instances instead of filtering on each cache call.
                                           private val byGroup: Ref[Task, MultiDict[Group, Participant]],
                                           private val workingByGroup: Ref[Task, MultiDict[Group, Participant]],
                                           private val healthyByGroup: Ref[Task, MultiDict[Group, Participant]], // Healthy means both healthy and working
                                         ) extends ParticipantsCache {
  private val logger = LoggerFactory.getLogger(getClass.getName)

  locally {
    import monix.execution.Scheduler.{global => scheduler}

    if (cacheRefreshInterval > 0) {
      // Initial cache load is made by ParticipantsCacheImpl#apply
      scheduler.scheduleAtFixedRate(cacheRefreshInterval.seconds, cacheRefreshInterval.seconds) {
        refreshCache()
          .onErrorHandle { e: Throwable => logger.error(s"Cannot refresh ${getClass.getName} cache.", e) }
          .runToFuture(scheduler)
        ()
      }
    }
  }

  private val publisher: DefaultPublisher[Participant] = new DefaultPublisher[Participant]()

  override def getAllGroups: Task[List[Group]] = byGroup.get.map(_.keySet).map(_.toList)

  override def getParticipantsAssociatedWithGroup(group: Group): Task[Vector[Participant]] =
    getParticipantsBelongingToGroup(byGroup, group)

  override def getWorkingParticipantsAssociatedWithGroup(group: Group): Task[Vector[Participant]] =
    getParticipantsBelongingToGroup(workingByGroup, group)

  override def getHealthyParticipantsAssociatedWithGroup(group: Group): Task[Vector[Participant]] =
    getParticipantsBelongingToGroup(healthyByGroup, group)

  private def getParticipantsBelongingToGroup(cache: Ref[Task, MultiDict[Group, Participant]], group: Group): Task[Vector[Participant]] =
    cache.get.map(_.get(group)).map(_.toVector)

  override def registerListener(listener: ChangeListener[Participant]): Task[Unit] =
    for {
      _ <- publisher.register(listener)
      cache <- byGroup.get
      _ <- listener.notifyAboutAdd(cache.values.toList) // initial load to the listener
    } yield ()

  private def refreshCache(): Task[Unit] =
    for {
      participants <- participantEventService.constructAllParticipants()
      groupsWithParticipants <- Task.eval(participants.map(p => (p.group, p)))
      prevCacheStates <- Task.parZip3(
        byGroup.getAndSet(MultiDict.from(groupsWithParticipants)),
        workingByGroup.set(MultiDict.from(groupsWithParticipants.filter(_._2.status == Working))),
        healthyByGroup.set(MultiDict.from(groupsWithParticipants.filter(t => t._2.status == Working && t._2.health == Healthy)))
      )
      (prevByGroupCacheState, _, _) = prevCacheStates
      _ <- Task.parZip2(
        // Find all participants that were present inside the cache, but either no longer are or some of their
        // properties changed and send an event to listeners about remove action.
        Task.eval(prevByGroupCacheState.values.toSet.removedAll(participants))
          .flatMap(participantsNoMorePresentInCache =>
            publisher.getListeners.flatMap(_.map(_.notifyAboutRemove(participantsNoMorePresentInCache)).sequence)
          ),
        // Send an event about gotten participants. The listener should handle duplicates on its own.
        // There is a place for optimization - we know that e.g. ConsistentHashingState discards all participants that
        // are either not working or not healthy. Because of that, we could simply put here healthyByGroup values.
        // If the participant was working/healthy and is not anymore, it will be in `participantsNoMorePresentInCache` anyway.
        // However, let's leave it as it is in order to have more generic listeners and go back here once we reach the bottleneck.
        publisher.getListeners.flatMap(_.map(_.notifyAboutAdd(participants)).sequence)
      )
    } yield ()
}

object ParticipantsCacheImpl {
  def apply(
             participantEventService: ParticipantEventService,
             initParticipants: List[Participant] = List.empty,
             cacheRefreshInterval: Int = -1 // By default, the background job is turned off and the cache is filled with initParticipants forever.
           ): Task[ParticipantsCacheImpl] =
    for {
      participants <- if (cacheRefreshInterval != -1) participantEventService.constructAllParticipants() else Task.eval(initParticipants)
      groupsWithParticipants <- Task.eval(participants.map(p => (p.group, p)))
      participantsByGroupRef <- Ref.of[Task, MultiDict[Group, Participant]](MultiDict.from(groupsWithParticipants))
      workingParticipantByGroupRef <- Ref.of[Task, MultiDict[Group, Participant]](
        MultiDict.from(groupsWithParticipants.filter(_._2.status == Working)))
      healthyParticipantByGroupRef <- Ref.of[Task, MultiDict[Group, Participant]](
        MultiDict.from(groupsWithParticipants).filter(t => t._2.status == Working && t._2.health == Healthy))
    } yield new ParticipantsCacheImpl(participantEventService, cacheRefreshInterval)(
      participantsByGroupRef,
      workingParticipantByGroupRef,
      healthyParticipantByGroupRef
    )
}
