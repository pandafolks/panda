package com.github.pandafolks.panda.healthcheck

import cats.effect.Resource
import com.github.pandafolks.panda.healthcheck.{UnsuccessfulHealthCheck, UnsuccessfulHealthCheckDaoImpl}
import monix.connect.mongodb.client.{CollectionCodecRef, CollectionOperator, MongoConnection}
import monix.eval.Task
import org.mongodb.scala.{ConnectionString, MongoClientSettings}
import org.scalacheck.Gen
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName

trait UnsuccessfulHealthCheckFixture {
  private val dbName = "test"
  protected val mongoContainer: MongoDBContainer = new MongoDBContainer(
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

  protected val unsuccessfulHealthCheckColName: String = randomString(
    UnsuccessfulHealthCheck.UNSUCCESSFUL_HEALTH_CHECK_COLLECTION_NAME
  )
  private val unsuccessfulHealthCheckCol: CollectionCodecRef[UnsuccessfulHealthCheck] =
    UnsuccessfulHealthCheck.getCollection(dbName, unsuccessfulHealthCheckColName)
  protected val unsuccessfulHealthCheckConnection: Resource[Task, CollectionOperator[UnsuccessfulHealthCheck]] =
    MongoConnection.create1(settings, unsuccessfulHealthCheckCol)

  protected val unsuccessfulHealthCheckDao: UnsuccessfulHealthCheckDaoImpl = new UnsuccessfulHealthCheckDaoImpl(
    unsuccessfulHealthCheckConnection
  )

  def randomString(prefix: String): String = Gen.uuid.map(prefix + _.toString.take(15)).sample.get

}
