package com.github.pandafolks.panda.utils

import monix.eval.Task

trait Publisher[T] {
  def register(listener: Listener[T]): Task[Unit]

  def unRegister(listener: Listener[T]): Task[Unit]

}
