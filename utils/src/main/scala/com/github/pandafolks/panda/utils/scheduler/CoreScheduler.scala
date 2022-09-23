package com.github.pandafolks.panda.utils.scheduler

import monix.execution.Scheduler
import monix.execution.Scheduler.Implicits.global

object CoreScheduler {
  implicit val scheduler: Scheduler = global
}
