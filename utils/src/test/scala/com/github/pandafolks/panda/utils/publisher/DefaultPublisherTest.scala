package com.github.pandafolks.panda.utils.publisher

import com.github.pandafolks.panda.utils.listener.ChangeListener
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.immutable

final class DefaultPublisherTest extends AsyncFlatSpec with Matchers with ScalaFutures {
  implicit val scheduler: SchedulerService = Scheduler.forkJoin(Runtime.getRuntime.availableProcessors(), Runtime.getRuntime.availableProcessors())

  private class FakeChangeListener() extends ChangeListener[Int] {
    override def notifyAboutAdd(items: immutable.Iterable[Int]): Task[Unit] = ???

    override def notifyAboutRemove(items: immutable.Iterable[Int]): Task[Unit] = ???
  }


  "getListeners" should "return currently registered listeners" in {
    val publisher = new DefaultPublisher[Int]()

    val changeListener1 = new FakeChangeListener()
    val changeListener2 = new FakeChangeListener()

    val f = (
      publisher.getListeners
        .flatMap(f => publisher.register(changeListener1).map(_ => f))
        .flatMap(f => publisher.register(changeListener2).map(_ => f))
        .flatMap(f => publisher.getListeners.map(r => (f, r)))
        .flatMap(f2 => publisher.unRegister(changeListener1).map(_ => f2))
        .flatMap(f2 => publisher.getListeners.map(r => (f2._1, f2._2, r)))
      ).runToFuture

    whenReady(f) { res =>
      res._1.size should be(0)

      res._2.size should be(2)
      res._2 should contain theSameElementsInOrderAs List(changeListener2, changeListener1)

      res._3.size should be(1)
      res._3 should contain theSameElementsInOrderAs List(changeListener2)
    }
  }
}
