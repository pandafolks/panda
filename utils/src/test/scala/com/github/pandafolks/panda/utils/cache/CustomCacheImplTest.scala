package com.github.pandafolks.panda.utils.cache

import com.github.pandafolks.panda.utils.scheduler.CoreScheduler
import monix.eval.Task
import monix.execution.Scheduler
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.duration.DurationInt

class CustomCacheImplTest extends AsyncFlatSpec with Matchers with ScalaFutures {

  implicit val scheduler: Scheduler = CoreScheduler.scheduler
  implicit val defaultConfig: PatienceConfig = PatienceConfig(30.seconds, 100.milliseconds)

  "cache" should "not exceed the maximumSize" in {
    val cache = new CustomCacheImpl[Int, Int](key => {
      Task.eval(key)
    })(maximumSize = 10, ttl = 30.seconds)

    val f = Task.traverse(Range.inclusive(1, 100).toList)(i => cache.get(i)).runToFuture

    whenReady(f) { res =>
      res.size should be(100)
      cache.underlyingGuavaCache.size() should be(10)
    }

    val f2 = Task.traverse(Range.inclusive(131, 230).toList)(i => cache.get(i)).runToFuture

    whenReady(f2) { res =>
      res.size should be(100)
      cache.underlyingGuavaCache.size() should be(10)
    }
  }

  it should "work with specified ttl" in {
    val map = new ConcurrentHashMap[Int, Int]()

    val cache = new CustomCacheImpl[Int, Int](key => {
      map.compute(key, (_, prev) => if (Option(prev).isEmpty) 1 else prev + 1)
      Task.eval(key)
    })(maximumSize = 10, ttl = 500.milli)

    val f = Task
      .traverse(Range.inclusive(1, 10).toList)(i => cache.get(i))
      .flatMap(_ => Task.traverse(Range.inclusive(1, 10).toList)(i => cache.get(i)))
      .flatMap(_ => Task.traverse(Range.inclusive(1, 10).toList)(i => cache.get(i)))
      .flatMap { _ => Task.sleep(600.milli) }
      .flatMap(_ => Task.traverse(Range.inclusive(1, 10).toList)(i => cache.get(i)))
      .flatMap(_ => Task.traverse(Range.inclusive(1, 10).toList)(i => cache.get(i)))
      .runToFuture

    whenReady(f) { res =>
      res.size should be(10)
      for { i <- 1 to 10 } {
        map.get(i) should be(2)
      }
      succeed
    }
  }

  "get" should "load value only once" in {
    val map = new ConcurrentHashMap[Int, Int]()

    val cache = new CustomCacheImpl[Int, Int](key => {
      map.compute(key, (_, prev) => if (Option(prev).isEmpty) 1 else prev + 1)
      Task.eval(key * 2)
    })(maximumSize = 150, ttl = 10.seconds)

    val f = Task
      .traverse(Range.inclusive(1, 100).toList)(i => cache.get(i))
      .flatMap(_ => Task.traverse(Range.inclusive(1, 100).toList)(i => cache.get(i)))
      .flatMap(_ => Task.traverse(Range.inclusive(1, 100).toList)(i => cache.get(i)))
      .flatMap(_ => Task.traverse(Range.inclusive(1, 100).toList)(i => cache.get(i)))
      .flatMap(_ => Task.traverse(Range.inclusive(1, 100).toList)(i => cache.get(i)))
      .flatMap(_ => Task.traverse(Range.inclusive(1, 100).toList)(i => cache.get(i)))
      .flatMap(_ => Task.traverse(Range.inclusive(1, 100).toList)(i => cache.get(i)))
      .flatMap(_ => Task.traverse(Range.inclusive(1, 100).toList)(i => cache.get(i)))
      .flatMap(_ => Task.traverse(Range.inclusive(1, 100).toList)(i => cache.get(i)))
      .runToFuture

    whenReady(f) { res =>
      res.size should be(100)
      res should be(Range.inclusive(1, 100).map(_ * 2))

      map.size() should be(100)
      for { i <- 1 to 100 } {
        map.get(i) should be(1)
      }
      succeed
    }
  }
}
