package com.github.pandafolks.panda.nodestracker

import cats.effect.Resource
import monix.connect.mongodb.client.{CollectionCodecRef, CollectionOperator, MongoConnection}
import monix.eval.Task
import org.mongodb.scala.{ConnectionString, MongoClientSettings}
import org.scalacheck.Gen
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName

trait NodeTrackerFixture {
  private val dbName = "test"
  protected val mongoContainer: MongoDBContainer = new MongoDBContainer(
    DockerImageName.parse("mongo").withTag("4.0.10")
  )
  mongoContainer.start()

  private val settings: MongoClientSettings =
    MongoClientSettings.builder()
      .applyToClusterSettings(builder => {
        builder.applyConnectionString(new ConnectionString(mongoContainer.getReplicaSetUrl(dbName)))
        ()
      }
      ).build()

  protected val nodesColName: String = randomString(Node.NODES_COLLECTION_NAME)
  protected val nodesCol: CollectionCodecRef[Node] = Node.getCollection(dbName, nodesColName)
  protected val nodesConnection: Resource[Task, CollectionOperator[Node]] = MongoConnection.create1(settings, nodesCol)

  private val nodeTrackerDao: NodeTrackerDao = new NodeTrackerDaoImpl(nodesConnection)
  protected val nodeTrackerService: NodeTrackerService = new NodeTrackerServiceImpl(nodeTrackerDao)(1)

  def randomString(prefix: String): String = Gen.uuid.map(prefix + _.toString.take(15)).sample.get
}

