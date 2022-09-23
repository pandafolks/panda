package com.github.pandafolks.panda.utils.listener

import monix.eval.Task

import scala.collection.immutable.Iterable

trait ChangeListener[T] {
  def notifyAboutAdd(items: Iterable[T]): Task[Unit]

  def notifyAboutRemove(items: Iterable[T]): Task[Unit]
}
