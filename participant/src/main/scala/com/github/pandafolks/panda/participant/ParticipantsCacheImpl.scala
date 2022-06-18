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

final class ParticipantsCacheImpl(private val participantEventService: ParticipantEventService,
                                  private val cacheRefreshInterval: Int)(
                                   private val cacheByGroup: Ref[Task, MultiDict[Group, Participant]]) extends ParticipantsCache {
  private val logger = LoggerFactory.getLogger(getClass.getName)

  locally {
    import monix.execution.Scheduler.{global => scheduler}

    if (cacheRefreshInterval > 0) {
      scheduler.scheduleAtFixedRate(0.seconds, cacheRefreshInterval.seconds) {
        refreshCache()
          .onErrorHandle { e: Throwable => logger.error(s"Cannot refresh ${getClass.getName} cache.", e) }
          .runToFuture(scheduler)
          ()
      }
    }
  }

  private val publisher: DefaultPublisher[Participant] = new DefaultPublisher[Participant]()

  override def getAllGroups: Task[List[Group]] = cacheByGroup.get.map(_.keySet).map(_.toList)

  override def getParticipantsAssociatedWithGroup(group: Group): Task[Vector[Participant]] =
    cacheByGroup.get.map(_.get(group)).map(_.toVector)

  override def getWorkingParticipantsAssociatedWithGroup(group: Group): Task[Vector[Participant]] =
    getParticipantsAssociatedWithGroup(group).map(_.filter(_.status == Working))

  override def registerListener(listener: ChangeListener[Participant]): Task[Unit] =
    for {
      _ <- publisher.register(listener)
      cache <- cacheByGroup.get
      _ <- listener.notifyAboutAdd(cache.values.toList) // initial load to the listener
    } yield ()

  private def refreshCache(): Task[Unit] =
    participantEventService.constructAllParticipants()
      .flatMap(participants =>
        cacheByGroup.getAndSet(MultiDict.from(participants.map(p => (p.group, p))))
          .flatMap(prevCacheState =>
            Task.parZip2(
              // Find all participants that were present inside the cache, but either no longer are or some of their
              // properties changed and send an event to listeners about remove action.
              Task.eval(prevCacheState.values.toSet.removedAll(participants))
                .flatMap(participantsNoMorePresentInCache =>
                  publisher.getListeners.flatMap(_.map(_.notifyAboutRemove(participantsNoMorePresentInCache)).sequence)),
              // Send an event about gotten participants. The listener should handle duplicates on its own.
              publisher.getListeners.flatMap(_.map(_.notifyAboutAdd(participants)).sequence)
            )
          ).void
      )
}

object ParticipantsCacheImpl {
  def apply(participantEventService: ParticipantEventService,
            initParticipants: List[Participant] = List.empty,
            cacheRefreshInterval: Int = -1): Task[ParticipantsCacheImpl] =
    for {
      participantsByGroupRef <- Ref.of[Task, MultiDict[Group, Participant]](
        MultiDict.from(initParticipants.map(p => (p.group, p))))
    } yield new ParticipantsCacheImpl(participantEventService, cacheRefreshInterval)(participantsByGroupRef)
}
