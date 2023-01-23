package com.github.pandafolks.panda.utils.queue

import monix.eval.Task
import monix.execution.{AsyncQueue, BufferCapacity, ChannelType, Scheduler}

final class MonixQueue[T] private (private val queue: AsyncQueue[T]) {
  def poll: Task[T] = Task.deferFuture(queue.poll())

  def offer(item: T): Task[Unit] = Task.deferFuture(queue.offer(item)).void

  def offerMany(items: Iterable[T]): Task[Unit] = Task.deferFuture(queue.offerMany(items)).void
}

object MonixQueue {
  def make[T](capacity: BufferCapacity, channelType: ChannelType)(scheduler: Scheduler): MonixQueue[T] =
    new MonixQueue(AsyncQueue.withConfig(capacity, channelType)(scheduler))
}
