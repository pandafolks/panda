package com.github.pandafolks.panda.loadbalancer

import cats.implicits.toTraverseOps
import com.github.pandafolks.panda.participant.Participant
import com.github.pandafolks.panda.routes.Group
import monix.eval.Task
import monix.execution.Scheduler
import com.github.pandafolks.panda.utils.scheduler.CoreScheduler
import org.scalatest.PrivateMethodTester
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers.be
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.time.{Seconds, Span}

import java.util.concurrent.ConcurrentHashMap
import scala.collection.immutable.TreeMap
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.util.Random

class ConsistentHashingStateTest extends AsyncFlatSpec with PrivateMethodTester with ScalaFutures {
  implicit final val scheduler: Scheduler = CoreScheduler.scheduler

  "get" should "always return appropriate identifier" in {
    val underTest = new ConsistentHashingState(positionsPerIdentifier = 500)

    val r = new Random(42)
    val group1 = Group("cars")
    val group2 = Group("planes")
    val group3 = Group("ships")

    for (i <- 1 to 500) {
      underTest.add(Participant("whatever", 1111, group1, group1.name + i))
      underTest.add(Participant("whatever", 1111, group2, group2.name + i))
      underTest.add(Participant("whatever", 1111, group3, group3.name + i))
    }

    var getCarsFutures: List[Future[Option[Participant]]] = List()
    for (_ <- 1 to 1000) {
      getCarsFutures = Future(underTest.get(group1, r.nextInt(Integer.MAX_VALUE))) :: getCarsFutures
    }
    var getShipsFutures: List[Future[Option[Participant]]] = List()
    for (_ <- 1 to 3000) {
      getShipsFutures = Future(underTest.get(group2, r.nextInt(Integer.MAX_VALUE))) :: getShipsFutures
    }
    var getPlanesFutures: List[Future[Option[Participant]]] = List()
    for (_ <- 1 to 200) {
      getPlanesFutures = Future(underTest.get(group3, r.nextInt(Integer.MAX_VALUE))) :: getPlanesFutures
    }

    val getCarsListWithFutures: Future[List[Option[Participant]]] = getCarsFutures.sequence
    val getShipsListWithFutures: Future[List[Option[Participant]]] = getShipsFutures.sequence
    val getPlanesListWithFutures: Future[List[Option[Participant]]] = getPlanesFutures.sequence


    whenReady(getCarsListWithFutures, Timeout.apply(Span.apply(10, Seconds))) {
      res =>
        res.map(r => r.isDefined should be(true))
        res.map(r => r.get.identifier.startsWith(group1.name) should be(true))
        res.size should be(1000)
    }

    whenReady(getShipsListWithFutures, Timeout.apply(Span.apply(10, Seconds))) {
      res =>
        res.map(r => r.isDefined should be(true))
        res.map(r => r.get.identifier.startsWith(group2.name) should be(true))
        res.size should be(3000)
    }

    whenReady(getPlanesListWithFutures, Timeout.apply(Span.apply(10, Seconds))) {
      res =>
        res.map(r => r.isDefined should be(true))
        res.map(r => r.get.identifier.startsWith(group3.name) should be(true))
        res.size should be(200)
    }
  }

  it should "return None if there is no requested group / the group is empty" in {
    val underTest = new ConsistentHashingState(positionsPerIdentifier = 500)

    val group1 = Group("cars")
    val group2 = Group("planes")
    val group3 = Group("ships")

    for (i <- 1 to 500) {
      underTest.add(Participant("whatever", 1111, group1, group1.name + i))
      underTest.add(Participant("whatever", 1111, group2, group2.name + i))
      underTest.add(Participant("whatever", 1111, group3, group3.name + i))
    }

    val requestedGroup = Group("blabla")
    underTest.get(requestedGroup, 3123123) should be (None)

    val p = Participant("whatever", 123123, requestedGroup, "someIden")
    underTest.add(p)
    underTest.get(requestedGroup, 3123123) should be (Some(p))

    underTest.remove(p)
    underTest.get(requestedGroup, 3123123) should be (None)
  }

  "add" should "not lose any data during concurrent invocations (one group case)" in {
    val underTest = new ConsistentHashingState(positionsPerIdentifier = 600)
    val usedPositionsGroupedByGroup = PrivateMethod[ConcurrentHashMap[Group, TreeMap[Int, Participant]]](Symbol("usedPositionsGroupedByGroup"))
    val usedIdentifiersWithPositions = PrivateMethod[ConcurrentHashMap[Participant, List[Int]]](Symbol("usedIdentifiersWithPositions"))

    val group = Group("cars")

    var futures: List[Future[Unit]] = List()
    for (i <- 1 to 500) {
      futures = Future(underTest.add(Participant("whatever", 123, group, "cars" + i))) :: futures
    }
    val futureWithList: Future[List[Unit]] = futures.sequence

    whenReady(futureWithList, Timeout.apply(Span.apply(10, Seconds))) {
      _ =>
        underTest.invokePrivate(usedPositionsGroupedByGroup()).keySet().size() should be(1)
        underTest.invokePrivate(usedPositionsGroupedByGroup()).get(group).size should be(300000)
        underTest.invokePrivate(usedIdentifiersWithPositions()).values().asScala.map(_.size should be(600))
        underTest.invokePrivate(usedIdentifiersWithPositions()).keySet().size() should be(500)
    }
  }

    it should "not lose any data during concurrent invocations (multiple groups case)" in {
      val underTest = new ConsistentHashingState(positionsPerIdentifier = 500)
      val usedPositionsGroupedByGroup = PrivateMethod[ConcurrentHashMap[Group, TreeMap[Int, Participant]]](Symbol("usedPositionsGroupedByGroup"))
      val usedIdentifiersWithPositions = PrivateMethod[ConcurrentHashMap[Participant, List[Int]]](Symbol("usedIdentifiersWithPositions"))

      val group1 = Group("cars")
      val group2 = Group("planes")
      val group3 = Group("ships")

      var futures: List[Future[Unit]] = List()
      for (i <- 1 to 500) {
        futures = Future(underTest.add(Participant("ip", 123, group1, group1.name + i))) :: futures
        futures = Future(underTest.add(Participant("ip", 123, group2, group2.name + i))) :: futures
        futures = Future(underTest.add(Participant("ip", 123, group3, group3.name + i))) :: futures
      }
      val futureWithList: Future[List[Unit]] = futures.sequence

      whenReady(futureWithList, Timeout.apply(Span.apply(10, Seconds))) {
        _ =>
          underTest.invokePrivate(usedPositionsGroupedByGroup()).keySet().size() should be(3)
          underTest.invokePrivate(usedPositionsGroupedByGroup()).values().asScala.map(_.size).sum should be(750000)
          underTest.invokePrivate(usedPositionsGroupedByGroup()).get(group1).size should be(250000)
          underTest.invokePrivate(usedPositionsGroupedByGroup()).get(group2).size should be(250000)
          underTest.invokePrivate(usedPositionsGroupedByGroup()).get(group3).size should be(250000)
          underTest.invokePrivate(usedIdentifiersWithPositions()).values().asScala.map(_.size should be(500))
          underTest.invokePrivate(usedIdentifiersWithPositions()).keySet().size() should be(1500)

      }
    }

      "remove" should "delete only requested identifier" in {
        val underTest = new ConsistentHashingState(positionsPerIdentifier = 500)
        val usedPositionsGroupedByGroup = PrivateMethod[ConcurrentHashMap[Group, TreeMap[Int, Participant]]](Symbol("usedPositionsGroupedByGroup"))
        val usedIdentifiersWithPositions = PrivateMethod[ConcurrentHashMap[Participant, List[Int]]](Symbol("usedIdentifiersWithPositions"))

        val group1 = Group("cars")
        val group2 = Group("planes")

        for (i <- 1 to 500) {
          underTest.add(Participant("ip", 123, group1, group1.name + i))
          underTest.add(Participant("ip", 123, group2, group2.name + i))
        }

        underTest.remove(Participant("ip", 123, group1, "cars100"))
        underTest.invokePrivate(usedPositionsGroupedByGroup()).keySet().size() should be(2)
        underTest.invokePrivate(usedPositionsGroupedByGroup()).values().asScala.map(_.size).sum should be(499500)
        underTest.invokePrivate(usedPositionsGroupedByGroup()).get(group1).size should be(249500)
        underTest.invokePrivate(usedPositionsGroupedByGroup()).get(group2).size should be(250000)
        underTest.invokePrivate(usedIdentifiersWithPositions()).values().asScala.map(_.size should be(500))
        underTest.invokePrivate(usedIdentifiersWithPositions()).keySet().size() should be(999)
        underTest.invokePrivate(usedIdentifiersWithPositions()).keySet().contains("cars100") should be(false)
      }

  it should "delete only requested identifiers (all identifiers from single group)" in {
    val underTest = new ConsistentHashingState(positionsPerIdentifier = 500)
    val usedPositionsGroupedByGroup = PrivateMethod[ConcurrentHashMap[Group, TreeMap[Int, Participant]]](Symbol("usedPositionsGroupedByGroup"))
    val usedIdentifiersWithPositions = PrivateMethod[ConcurrentHashMap[Participant, List[Int]]](Symbol("usedIdentifiersWithPositions"))

    val group1 = Group("cars")
    val group2 = Group("planes")

    for (i <- 1 to 500) {
      underTest.add(Participant("ip", 123, group1, group1.name + i))
      underTest.add(Participant("ip", 123, group2, group2.name + i))
    }

    var futures: List[Future[Unit]] = List()
    for (i <- 1 to 500) {
      futures = Future(underTest.remove(Participant("ip", 123, group1, group1.name + i))) :: futures
    }
    val futureWithList: Future[List[Unit]] = futures.sequence


    whenReady(futureWithList, Timeout.apply(Span.apply(10, Seconds))) {
      _ =>
        underTest.invokePrivate(usedPositionsGroupedByGroup()).keySet().size() should be(2)
        underTest.invokePrivate(usedPositionsGroupedByGroup()).get(group1).size should be(0)
        underTest.invokePrivate(usedPositionsGroupedByGroup()).get(group2).size should be(250000)

        underTest.invokePrivate(usedIdentifiersWithPositions()).values().asScala.map(_.size should be(500))
        underTest.invokePrivate(usedIdentifiersWithPositions()).keySet().asScala.count(k => k.identifier.startsWith("cars")) should be (0)
        underTest.invokePrivate(usedIdentifiersWithPositions()).keySet().size() should be(500)
    }
  }

  it should "delete only requested identifiers (multiple groups)" in {
    val underTest = new ConsistentHashingState(positionsPerIdentifier = 500)
    val usedPositionsGroupedByGroup = PrivateMethod[ConcurrentHashMap[Group, TreeMap[Int, Participant]]](Symbol("usedPositionsGroupedByGroup"))
    val usedIdentifiersWithPositions = PrivateMethod[ConcurrentHashMap[Participant, List[Int]]](Symbol("usedIdentifiersWithPositions"))

    val group1 = Group("cars")
    val group2 = Group("planes")
    val group3 = Group("ships")

    for (i <- 1 to 500) {
      underTest.add(Participant("ip", 123, group1, group1.name + i))
      underTest.add(Participant("ip", 123, group2, group2.name + i))
      underTest.add(Participant("ip", 123, group3, group3.name + i))
    }

    var futures: List[Future[Unit]] = List()
    for (i <- 1 to 400 by 4) {
      futures = Future(underTest.remove(Participant("ip", 123, group1, group1.name + i))) :: futures
      futures = Future(underTest.remove(Participant("ip", 123, group3, group3.name + i))) :: futures
      futures = Future(underTest.remove(Participant("ip", 123, group2, group2.name + i))) :: futures

    }
    val futureWithList: Future[List[Unit]] = futures.sequence


    whenReady(futureWithList, Timeout.apply(Span.apply(10, Seconds))) {
      _ =>
        underTest.invokePrivate(usedPositionsGroupedByGroup()).keySet().size() should be(3)
        underTest.invokePrivate(usedPositionsGroupedByGroup()).get(group1).size should be(200000)
        underTest.invokePrivate(usedPositionsGroupedByGroup()).get(group2).size should be(200000)
        underTest.invokePrivate(usedPositionsGroupedByGroup()).get(group3).size should be(200000)


        underTest.invokePrivate(usedIdentifiersWithPositions()).values().asScala.map(_.size should be(500))
        underTest.invokePrivate(usedIdentifiersWithPositions()).keySet().asScala.count(k => k.identifier.startsWith(group1.name)) should be (400)
        underTest.invokePrivate(usedIdentifiersWithPositions()).keySet().asScala.count(k => k.identifier.startsWith(group2.name)) should be (400)
        underTest.invokePrivate(usedIdentifiersWithPositions()).keySet().asScala.count(k => k.identifier.startsWith(group3.name)) should be (400)
    }
  }

  it should "clear empty groups" in {
    val underTest = new ConsistentHashingState(positionsPerIdentifier = 500)
    val usedPositionsGroupedByGroup = PrivateMethod[ConcurrentHashMap[Group, TreeMap[Int, Participant]]](Symbol("usedPositionsGroupedByGroup"))
    val underTestMethod = PrivateMethod[Task[Unit]](Symbol("clearEmptyGroups"))

    val group1 = Group("cars")
    val group2 = Group("planes")

    for (i <- 1 to 500) {
      underTest.add(Participant("ip", 123, group1, group1.name + i))
      underTest.add(Participant("ip", 123, group2, group2.name + i))
    }

    var futures: List[Future[Unit]] = List()
    for (i <- 1 to 500) {
      futures = Future(underTest.remove(Participant("ip", 123, group1, group1.name + i))) :: futures
      futures = Future(underTest.remove(Participant("ip", 123, group2, group2.name + i))) :: futures
    }
    val futureWithList: Future[List[Unit]] = futures.sequence

    val f = futureWithList.map(_ => (
      underTest.invokePrivate(usedPositionsGroupedByGroup()).keySet().size(),
      underTest.invokePrivate(usedPositionsGroupedByGroup()).get(group1).isEmpty,
      underTest.invokePrivate(usedPositionsGroupedByGroup()).get(group2).isEmpty
    )).flatMap(res => underTest.invokePrivate(underTestMethod()).runToFuture.map(_ => res))

    whenReady(f, Timeout.apply(Span.apply(10, Seconds))) {
      res =>
        res._1 should be(2)
        res._2 should be(true)
        res._3 should be(true)
    }
    underTest.invokePrivate(usedPositionsGroupedByGroup()).keySet().size() should be(0)
  }
}
