package com.github.mattszm.panda.utils
import cats.effect.concurrent.Ref
import monix.eval.Task

final class DefaultPublisher[T] extends Publisher[T] {
  private val changeAwareItems: Ref[Task, List[ChangeListener[T]]] = Ref.unsafe(List.empty)

  override def register(listener: ChangeListener[T]): Task[Unit] =
    changeAwareItems.update(prevState => listener :: prevState)

  override def unRegister(listener: ChangeListener[T]): Task[Unit] =
    changeAwareItems.update(prevState => prevState.filterNot(_ == listener))

  def getListeners: Task[List[ChangeListener[T]]] = changeAwareItems.get
}
