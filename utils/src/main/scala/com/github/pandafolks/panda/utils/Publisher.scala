package com.github.pandafolks.panda.utils

import com.github.pandafolks.panda.utils.listener.ChangeListener
import monix.eval.Task

trait Publisher[T] {
  def register(listener: ChangeListener[T]): Task[Unit]

  def unRegister(listener: ChangeListener[T]): Task[Unit]

}
