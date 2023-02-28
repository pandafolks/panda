package com.github.pandafolks.panda.sequence

import com.github.pandafolks.panda.utils.PersistenceError
import monix.connect.mongodb.client.{CollectionCodecRef, MongoConnection}
import monix.eval.Task
import monix.execution.schedulers.SchedulerService
import monix.execution.{CancelableFuture, Scheduler}
import org.mongodb.scala.bson.BsonInt64
import org.mongodb.scala.{ConnectionString, MongoClientSettings}
import org.scalacheck.Gen
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, EitherValues}
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName

import scala.concurrent.duration.DurationInt

class SequenceDaoImplItTest extends AsyncFlatSpec with Matchers with ScalaFutures with EitherValues with BeforeAndAfterAll {
  implicit val scheduler: SchedulerService =
    Scheduler.forkJoin(Runtime.getRuntime.availableProcessors() * 2, Runtime.getRuntime.availableProcessors() * 2)

  implicit val defaultConfig: PatienceConfig = PatienceConfig(30.seconds, 100.milliseconds)

  private val dbName: String = randomString("test")
  private val mongoContainer: MongoDBContainer = new MongoDBContainer(
    DockerImageName.parse("mongo").withTag("latest")
  )
  mongoContainer.start()

  private val settings: MongoClientSettings =
    MongoClientSettings
      .builder()
      .applyToClusterSettings(builder => {
        builder.applyConnectionString(new ConnectionString(mongoContainer.getReplicaSetUrl(dbName)))
        ()
      })
      .build()

  private val sequenceColName: String = randomString(Sequence.SEQUENCE_COLLECTION_NAME)
  private val sequenceCol: CollectionCodecRef[Sequence] = Sequence.getCollection(dbName, sequenceColName)
  private val sequenceConnection = MongoConnection.create1(settings, sequenceCol)

  private val sequenceDao: SequenceDao = new SequenceDaoImpl

  override protected def afterAll(): Unit = mongoContainer.stop()

  def randomString(prefix: String): String = Gen.uuid.map(prefix + _.toString.take(15)).sample.get

  "SequenceDaoImpl#getNextSequence" should "generate unique sequence values in a multi-threading environment" in {
    val sequenceKey = SequenceKey(randomString("KEY0"))

    val results: CancelableFuture[List[Either[PersistenceError, BsonInt64]]] = sequenceConnection
      .use(c => Task.parTraverseUnordered(1 to 10000)(_ => sequenceDao.getNextSequence(sequenceKey, c)))
      .runToFuture(scheduler)

    whenReady(results) { res =>
      res.map(_.map(_.intValue())) should contain theSameElementsAs (for (i <- 1 to 10000) yield Right(i)).toList
    }
  }

  it should "be able to handle multiple sequence keys" in {
    val sequenceKey1 = SequenceKey(randomString("KEY1"))
    val sequenceKey2 = SequenceKey(randomString("KEY2"))
    val sequenceKey3 = SequenceKey(randomString("KEY3"))
    val sequenceKey4 = SequenceKey(randomString("KEY4"))

    val results: CancelableFuture[
      (
        List[Either[PersistenceError, BsonInt64]],
          List[Either[PersistenceError, BsonInt64]],
          List[Either[PersistenceError, BsonInt64]],
          List[Either[PersistenceError, BsonInt64]]

        )
    ] =
      sequenceConnection
        .use(c =>
          Task.parZip3(
            Task.parTraverseUnordered(1 to 1000)(_ => sequenceDao.getNextSequence(sequenceKey1, c)),
            Task.parTraverseUnordered(1 to 600)(_ => sequenceDao.getNextSequence(sequenceKey2, c)),
            Task.parTraverseUnordered(1 to 800)(_ => sequenceDao.getNextSequence(sequenceKey3, c))
          ).flatMap(p => Task.parTraverseUnordered(1 to 1100)(_ => sequenceDao.getNextSequence(sequenceKey4, c))
            .map(p2 => (p._1, p._2, p._3, p2)))
        )
        .runToFuture(scheduler)

    whenReady(results) { res =>
      res._1.map(_.map(_.intValue())) should contain theSameElementsAs (for (i <- 1 to 1000) yield Right(i)).toList
      res._2.map(_.map(_.intValue())) should contain theSameElementsAs (for (i <- 1 to 600) yield Right(i)).toList
      res._3.map(_.map(_.intValue())) should contain theSameElementsAs (for (i <- 1 to 800) yield Right(i)).toList
      res._4.map(_.map(_.intValue())) should contain theSameElementsAs (for (i <- 1 to 1100) yield Right(i)).toList
    }
  }
}
