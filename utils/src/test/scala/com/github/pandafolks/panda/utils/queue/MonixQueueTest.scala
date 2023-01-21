package com.github.pandafolks.panda.utils.queue

import com.github.pandafolks.panda.utils.scheduler.CoreScheduler.scheduler
import monix.eval.Task
import monix.execution.BufferCapacity
import monix.execution.ChannelType.MPMC
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.DurationInt

final class MonixQueueTest extends AsyncFlatSpec with Matchers with ScalaFutures {
  implicit val defaultConfig: PatienceConfig = PatienceConfig(5.seconds, 100.milliseconds)

  "offer and poll" should "collaborate" in {
    val queue = MonixQueue.make[Int](BufferCapacity.Unbounded(), MPMC)

    val itemsNumber = 10000

    val f = (
      Task.traverse(Range.inclusive(1, itemsNumber).toList)(queue.offer)
        >> Task.sequence(List.fill(itemsNumber)(queue.poll))
      ).runToFuture

    whenReady(f) { res =>
      res should contain theSameElementsInOrderAs Range.inclusive(1, itemsNumber).toList
    }
  }

  it should "collaborate with many concurrent requests" in {
    val queue = MonixQueue.make[Int](BufferCapacity.Unbounded(), MPMC)

    val itemsNumber = 10000

    val f = (
      Task.parTraverse(Range.inclusive(1, itemsNumber).toList)(queue.offer)
        >> Task.parSequence(List.fill(itemsNumber)(queue.poll))
      ).runToFuture

    whenReady(f) { res =>
      res should contain theSameElementsAs Range.inclusive(1, itemsNumber).toList
    }
  }

  "offerMany and poll" should "collaborate" in {
    val queue = MonixQueue.make[Int](BufferCapacity.Unbounded(), MPMC)

    val f = (
      Task.traverse(List.fill(50)(Range.inclusive(1, 2000).toList))(queue.offerMany)
        >> Task.sequence(List.fill(50 * 2000)(queue.poll))
      ).runToFuture

    whenReady(f) { res =>
      res should contain theSameElementsInOrderAs List.fill(50)(Range.inclusive(1, 2000).toList).flatten
    }
  }

  it should "collaborate with many concurrent requests" in {
    val queue = MonixQueue.make[Int](BufferCapacity.Unbounded(), MPMC)

    val f = (
      Task.parTraverse(List.fill(50)(Range.inclusive(1, 2000).toList))(queue.offerMany)
        >> Task.parSequence(List.fill(50 * 2000)(queue.poll))
      ).runToFuture

    whenReady(f) { res =>
      res should contain theSameElementsAs List.fill(50)(Range.inclusive(1, 2000).toList).flatten
    }
  }
}
