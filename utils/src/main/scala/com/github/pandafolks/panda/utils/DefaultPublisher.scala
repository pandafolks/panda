package com.github.pandafolks.panda.utils

import cats.effect.concurrent.Ref
import monix.eval.Task

final class DefaultPublisher[T] extends Publisher[T] {
  private val changeAwareItems: Ref[Task, List[Listener[T]]] = Ref.unsafe(List.empty)

  override def register(listener: Listener[T]): Task[Unit] =
    changeAwareItems.update(prevState => listener :: prevState)

  override def unRegister(listener: Listener[T]): Task[Unit] =
    changeAwareItems.update(prevState => prevState.filterNot(_ == listener))

  def getListeners: Task[List[Listener[T]]] = changeAwareItems.get
}
