package com.github.mattszm.panda.loadbalancer

import cats.implicits.toTraverseOps
import com.github.mattszm.panda.routes.Group
import org.scalatest.PrivateMethodTester
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers.be
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.time.{Seconds, Span}

import java.util.concurrent.ConcurrentHashMap
import scala.collection.immutable.TreeMap
import scala.concurrent.ExecutionContext.global
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.jdk.CollectionConverters._
import scala.util.Random

class ConsistentHashingStateTest extends AsyncFlatSpec with PrivateMethodTester with ScalaFutures {
  implicit final val scheduler: ExecutionContextExecutor = global

  "get" should "always return appropriate identifier" in {
    val underTest = new ConsistentHashingState(positionsPerIdentifier = 500)

    val r = new Random(42)
    val group1 = Group("cars")
    val group2 = Group("planes")
    val group3 = Group("ships")

    for (i <- 1 to 500) {
      underTest.add(group1, group1.name + i)
      underTest.add(group2, group2.name + i)
      underTest.add(group3, group3.name + i)
    }

    var getCarsFutures: List[Future[Option[String]]] = List()
    for (_ <- 1 to 1000) {
      getCarsFutures = Future(underTest.get(group1, r.nextInt(Integer.MAX_VALUE))) :: getCarsFutures
    }
    var getShipsFutures: List[Future[Option[String]]] = List()
    for (_ <- 1 to 3000) {
      getShipsFutures = Future(underTest.get(group2, r.nextInt(Integer.MAX_VALUE))) :: getShipsFutures
    }
    var getPlanesFutures: List[Future[Option[String]]] = List()
    for (_ <- 1 to 200) {
      getPlanesFutures = Future(underTest.get(group3, r.nextInt(Integer.MAX_VALUE))) :: getPlanesFutures
    }

    val getCarsListWithFutures: Future[List[Option[String]]] = getCarsFutures.sequence
    val getShipsListWithFutures: Future[List[Option[String]]] = getShipsFutures.sequence
    val getPlanesListWithFutures: Future[List[Option[String]]] = getPlanesFutures.sequence


    whenReady(getCarsListWithFutures, Timeout.apply(Span.apply(10, Seconds))) {
      res =>
        res.map(r => r.isDefined should be(true))
        res.map(r => r.get.startsWith(group1.name) should be(true))
        res.size should be(1000)
    }

    whenReady(getShipsListWithFutures, Timeout.apply(Span.apply(10, Seconds))) {
      res =>
        res.map(r => r.isDefined should be(true))
        res.map(r => r.get.startsWith(group2.name) should be(true))
        res.size should be(3000)
    }

    whenReady(getPlanesListWithFutures, Timeout.apply(Span.apply(10, Seconds))) {
      res =>
        res.map(r => r.isDefined should be(true))
        res.map(r => r.get.startsWith(group3.name) should be(true))
        res.size should be(200)
    }
  }

  it should "return None if there is neither requested group nor the group is empty" in {
    val underTest = new ConsistentHashingState(positionsPerIdentifier = 500)

    val group1 = Group("cars")
    val group2 = Group("planes")
    val group3 = Group("ships")

    for (i <- 1 to 500) {
      underTest.add(group1, group1.name + i)
      underTest.add(group2, group2.name + i)
      underTest.add(group3, group3.name + i)
    }

    val requestedGroup = Group("blabla")
    underTest.get(requestedGroup, 3123123) should be (None)

    underTest.add(requestedGroup, "someIden")
    underTest.get(requestedGroup, 3123123) should be (Some("someIden"))

    underTest.remove(requestedGroup, "someIden")
    underTest.get(requestedGroup, 3123123) should be (None)
  }

  "add" should "not lose any data during concurrent invocations (one group case)" in {
    val underTest = new ConsistentHashingState(positionsPerIdentifier = 600)
    val usedPositionsGroupedByGroup = PrivateMethod[ConcurrentHashMap[Group, TreeMap[Int, String]]](Symbol("usedPositionsGroupedByGroup"))
    val usedIdentifiersWithPositions = PrivateMethod[ConcurrentHashMap[String, List[Int]]](Symbol("usedIdentifiersWithPositions"))

    val group = Group("cars")

    var futures: List[Future[Unit]] = List()
    for (i <- 1 to 500) {
      futures = Future(underTest.add(group, "cars" + i)) :: futures
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
      val usedPositionsGroupedByGroup = PrivateMethod[ConcurrentHashMap[Group, TreeMap[Int, String]]](Symbol("usedPositionsGroupedByGroup"))
      val usedIdentifiersWithPositions = PrivateMethod[ConcurrentHashMap[String, List[Int]]](Symbol("usedIdentifiersWithPositions"))

      val group1 = Group("cars")
      val group2 = Group("planes")
      val group3 = Group("ships")

      var futures: List[Future[Unit]] = List()
      for (i <- 1 to 500) {
        futures = Future(underTest.add(group1, group1.name + i)) :: futures
        futures = Future(underTest.add(group2, group2.name + i)) :: futures
        futures = Future(underTest.add(group3, group3.name + i)) :: futures
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
        val usedPositionsGroupedByGroup = PrivateMethod[ConcurrentHashMap[Group, TreeMap[Int, String]]](Symbol("usedPositionsGroupedByGroup"))
        val usedIdentifiersWithPositions = PrivateMethod[ConcurrentHashMap[String, List[Int]]](Symbol("usedIdentifiersWithPositions"))

        val group1 = Group("cars")
        val group2 = Group("planes")

        for (i <- 1 to 500) {
          underTest.add(group1, group1.name + i)
          underTest.add(group2, group2.name + i)
        }

        underTest.remove(group1, "cars100")
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
    val usedPositionsGroupedByGroup = PrivateMethod[ConcurrentHashMap[Group, TreeMap[Int, String]]](Symbol("usedPositionsGroupedByGroup"))
    val usedIdentifiersWithPositions = PrivateMethod[ConcurrentHashMap[String, List[Int]]](Symbol("usedIdentifiersWithPositions"))

    val group1 = Group("cars")
    val group2 = Group("planes")

    for (i <- 1 to 500) {
      underTest.add(group1, group1.name + i)
      underTest.add(group2, group2.name + i)
    }

    var futures: List[Future[Unit]] = List()
    for (i <- 1 to 500) {
      futures = Future(underTest.remove(group1, group1.name + i)) :: futures
    }
    val futureWithList: Future[List[Unit]] = futures.sequence


    whenReady(futureWithList, Timeout.apply(Span.apply(10, Seconds))) {
      _ =>
        underTest.invokePrivate(usedPositionsGroupedByGroup()).keySet().size() should be(2)
        underTest.invokePrivate(usedPositionsGroupedByGroup()).get(group1).size should be(0)
        underTest.invokePrivate(usedPositionsGroupedByGroup()).get(group2).size should be(250000)

        underTest.invokePrivate(usedIdentifiersWithPositions()).values().asScala.map(_.size should be(500))
        underTest.invokePrivate(usedIdentifiersWithPositions()).keySet().asScala.count(k => k.startsWith("cars")) should be (0)
        underTest.invokePrivate(usedIdentifiersWithPositions()).keySet().size() should be(500)
    }
  }

  it should "delete only requested identifiers (multiple groups)" in {
    val underTest = new ConsistentHashingState(positionsPerIdentifier = 500)
    val usedPositionsGroupedByGroup = PrivateMethod[ConcurrentHashMap[Group, TreeMap[Int, String]]](Symbol("usedPositionsGroupedByGroup"))
    val usedIdentifiersWithPositions = PrivateMethod[ConcurrentHashMap[String, List[Int]]](Symbol("usedIdentifiersWithPositions"))

    val group1 = Group("cars")
    val group2 = Group("planes")
    val group3 = Group("ships")

    for (i <- 1 to 500) {
      underTest.add(group1, group1.name + i)
      underTest.add(group2, group2.name + i)
      underTest.add(group3, group3.name + i)
    }

    var futures: List[Future[Unit]] = List()
    for (i <- 1 to 400 by 4) {
      futures = Future(underTest.remove(group1, group1.name + i.toString)) :: futures
      futures = Future(underTest.remove(group3, group3.name + i.toString)) :: futures
      futures = Future(underTest.remove(group2, group2.name + i.toString)) :: futures

    }
    val futureWithList: Future[List[Unit]] = futures.sequence


    whenReady(futureWithList, Timeout.apply(Span.apply(10, Seconds))) {
      _ =>
        underTest.invokePrivate(usedPositionsGroupedByGroup()).keySet().size() should be(3)
        underTest.invokePrivate(usedPositionsGroupedByGroup()).get(group1).size should be(200000)
        underTest.invokePrivate(usedPositionsGroupedByGroup()).get(group2).size should be(200000)
        underTest.invokePrivate(usedPositionsGroupedByGroup()).get(group3).size should be(200000)


        underTest.invokePrivate(usedIdentifiersWithPositions()).values().asScala.map(_.size should be(500))
        underTest.invokePrivate(usedIdentifiersWithPositions()).keySet().asScala.count(k => k.startsWith(group1.name)) should be (400)
        underTest.invokePrivate(usedIdentifiersWithPositions()).keySet().asScala.count(k => k.startsWith(group2.name)) should be (400)
        underTest.invokePrivate(usedIdentifiersWithPositions()).keySet().asScala.count(k => k.startsWith(group3.name)) should be (400)
    }
  }
}
