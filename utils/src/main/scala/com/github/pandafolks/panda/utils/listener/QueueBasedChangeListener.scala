package com.github.pandafolks.panda.utils.listener

import com.github.pandafolks.panda.utils.queue.MonixQueue
import monix.eval.Task
import monix.execution.{BufferCapacity, Cancelable, Scheduler}
import monix.execution.ChannelType.MPMC
import monix.reactive.Observable
import org.slf4j.LoggerFactory

import scala.annotation.unused
import scala.collection.immutable

abstract class QueueBasedChangeListener[T] extends ChangeListener[T] {
  private val logger = LoggerFactory.getLogger(getClass.getName)

  protected implicit val scheduler: Scheduler

  private val addQueue: MonixQueue[T] = MonixQueue.make(BufferCapacity.Unbounded(), MPMC)(scheduler)
  private val removeQueue: MonixQueue[T] = MonixQueue.make(BufferCapacity.Unbounded(), MPMC)(scheduler)

  @unused("Working as a background job")
  private val addQueuePollLoop: Cancelable = Observable
    .repeatEvalF(addQueue.poll)
    .observeOn(scheduler)
    .mapEval(notifyAboutAddInternal)
    .doOnComplete(Task.eval(logger.warn("addQueuePollLoop has been completed")))
    .doOnError(e => Task(logger.warn("addQueuePollLoop stopped abnormally", e)))
    .subscribe()(scheduler)

  @unused("Working as a background job")
  private val removeQueuePollLoop: Cancelable = Observable
    .repeatEvalF(removeQueue.poll)
    .observeOn(scheduler)
    .mapEval(notifyAboutRemoveInternal)
    .doOnComplete(Task.eval(logger.warn("removeQueuePollLoop has been completed")))
    .doOnError(e => Task(logger.warn("removeQueuePollLoop stopped abnormally", e)))
    .subscribe()(scheduler)

  protected def notifyAboutAddInternal(item: T): Task[Unit]

  protected def notifyAboutRemoveInternal(item: T): Task[Unit]

  override def notifyAboutAdd(items: immutable.Iterable[T]): Task[Unit] = addQueue.offerMany(items).void

  override def notifyAboutRemove(items: immutable.Iterable[T]): Task[Unit] = removeQueue.offerMany(items).void
}
