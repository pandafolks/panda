package com.github.mattszm.panda.utils

import monix.eval.Task

trait ChangeListener[T] {
  def notifyAboutAdd(items: List[T]): Task[Unit]

  def notifyAboutRemove(items: List[T]): Task[Unit]
}
