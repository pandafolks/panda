package com.github.pandafolks.panda.utils

import monix.eval.Task

import scala.collection.immutable.Iterable

trait Listener[T] {
  def notifyAboutAdd(items: Iterable[T]): Task[Unit]

  def notifyAboutRemove(items: Iterable[T]): Task[Unit]
}