package com.github.mattszm.panda.utils

import monix.execution.Scheduler
import monix.execution.Scheduler.global

object TestScheduler {
  implicit final val scheduler: Scheduler = global
}
