package com.github.pandafolks.panda.utils.listener

import monix.eval.Task
import monix.execution.Scheduler
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.jdk.CollectionConverters._


final class QueueBasedChangeListenerTest extends AsyncFlatSpec with Matchers with ScalaFutures {
  private implicit val testingScheduler: Scheduler = Scheduler.forkJoin(Runtime.getRuntime.availableProcessors(), Runtime.getRuntime.availableProcessors())

  "notifyAboutAdd" should "process the items by the overwritten notifyAboutAddInternal method" in {
    import java.util
    import java.util.Collections
    val memo = Collections.synchronizedList(new util.ArrayList[Int])

    val itemsNumber = 10000

    val queue = new QueueBasedChangeListener[Int] {
      protected lazy implicit val scheduler: Scheduler = testingScheduler

      override protected def notifyAboutAddInternal(item: Int): Task[Unit] = Task.eval(memo.add(item * 2)).void

      override protected def notifyAboutRemoveInternal(item: Int): Task[Unit] = ???
    }

    val request = queue.notifyAboutAdd(Range.inclusive(1, itemsNumber)).runToFuture

    whenReady(request) { _ =>
      while (memo.size() != itemsNumber) {
      }
      memo.asScala should be(Range.inclusive(1, itemsNumber).map(_ * 2))
    }
  }

  it should "be able to handle many concurrent method calls" in {
    import java.util
    import java.util.Collections
    val memo = Collections.synchronizedList(new util.ArrayList[Int])

    val queue = new QueueBasedChangeListener[Int] {
      protected lazy implicit val scheduler: Scheduler = testingScheduler

      override protected def notifyAboutAddInternal(item: Int): Task[Unit] = Task.eval(memo.add(item * 3)).void

      override protected def notifyAboutRemoveInternal(item: Int): Task[Unit] = ???
    }

    val request = Task.parSequence(List.fill(20)(queue.notifyAboutAdd(Range.inclusive(1, 400)))).runToFuture

    whenReady(request) { _ =>
      while (memo.size() != 20 * 400) {
      }

      memo.asScala should contain theSameElementsAs List.fill(20)(Range.inclusive(1, 400)).flatten.map(_ * 3)
    }
  }

  "notifyAboutRemove" should "process the items by the overwritten notifyAboutRemoveInternal method" in {
    import java.util
    import java.util.Collections
    val memo = Collections.synchronizedList(new util.ArrayList[Int])

    val itemsNumber = 10000

    val queue = new QueueBasedChangeListener[Int] {
      protected lazy implicit val scheduler: Scheduler = testingScheduler

      override protected def notifyAboutAddInternal(item: Int): Task[Unit] = ???

      override protected def notifyAboutRemoveInternal(item: Int): Task[Unit] = Task.eval(memo.add(item * 2)).void
    }

    val request = queue.notifyAboutRemove(Range.inclusive(1, itemsNumber)).runToFuture

    whenReady(request) { _ =>
      while (memo.size() != itemsNumber) {
      }
      memo.asScala should be(Range.inclusive(1, itemsNumber).map(_ * 2))
    }
  }

  it should "be able to handle many concurrent method calls" in {
    import java.util
    import java.util.Collections
    val memo = Collections.synchronizedList(new util.ArrayList[Int])

    val queue = new QueueBasedChangeListener[Int] {
      protected lazy implicit val scheduler: Scheduler = testingScheduler

      override protected def notifyAboutAddInternal(item: Int): Task[Unit] = ???

      override protected def notifyAboutRemoveInternal(item: Int): Task[Unit] = Task.eval(memo.add(item * 3)).void
    }

    val request = Task.parSequence(List.fill(200)(queue.notifyAboutRemove(Range.inclusive(1, 200)))).runToFuture

    whenReady(request) { _ =>
      while (memo.size() != 200 * 200) {
      }

      memo.asScala should contain theSameElementsAs List.fill(200)(Range.inclusive(1, 200)).flatten.map(_ * 3)
    }
  }

  it should "work independently from notifyAboutAdd" in {

    import java.util
    import java.util.Collections
    val notifyAboutAddMemo = Collections.synchronizedList(new util.ArrayList[Int])
    val notifyAboutRemoveMemo = Collections.synchronizedList(new util.ArrayList[Int])

    val queue = new QueueBasedChangeListener[Int] {
      protected lazy implicit val scheduler: Scheduler = testingScheduler

      override protected def notifyAboutAddInternal(item: Int): Task[Unit] = Task.eval(notifyAboutAddMemo.add(item * 13)).void

      override protected def notifyAboutRemoveInternal(item: Int): Task[Unit] = Task.eval(notifyAboutRemoveMemo.add(item * 17)).void
    }

    val request = Task.parZip2(
      Task.parSequence(List.fill(60)(queue.notifyAboutAdd(Range.inclusive(1, 210)))),
      Task.parSequence(List.fill(20)(queue.notifyAboutRemove(Range.inclusive(1, 350))))
    ).runToFuture

    whenReady(request) { _ =>
      while (notifyAboutAddMemo.size() != 60 * 210 && notifyAboutRemoveMemo.size() != 20 * 350) {
      }

      notifyAboutAddMemo.asScala should contain theSameElementsAs List.fill(60)(Range.inclusive(1, 210)).flatten.map(_ * 13)
      notifyAboutRemoveMemo.asScala should contain theSameElementsAs List.fill(20)(Range.inclusive(1, 350)).flatten.map(_ * 17)
    }
  }
}
