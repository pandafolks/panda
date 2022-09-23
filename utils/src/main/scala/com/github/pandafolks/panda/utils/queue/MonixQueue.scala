package com.github.pandafolks.panda.utils.queue

import monix.eval.Task
import monix.execution.{AsyncQueue, BufferCapacity, ChannelType}
import com.github.pandafolks.panda.utils.scheduler.CoreScheduler.scheduler

final class MonixQueue[T] private(queue: AsyncQueue[T]) {
  def poll: Task[T] = Task.deferFuture(queue.poll())

  def offer(item: T): Task[Unit] = Task.eval(queue.offer(item)).void

  def offerMany(items: Iterable[T]): Task[Unit] = Task.eval(queue.offerMany(items)).void
}

object MonixQueue {
  def make[T](capacity: BufferCapacity, channelType: ChannelType): MonixQueue[T] =
    new MonixQueue(AsyncQueue.withConfig(capacity, channelType))
}
