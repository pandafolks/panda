package com.github.mattszm.panda.utils

import monix.eval.Task

trait Publisher[T] {
  def register(listener: ChangeListener[T]): Task[Unit]

  def unRegister(listener: ChangeListener[T]): Task[Unit]

}
