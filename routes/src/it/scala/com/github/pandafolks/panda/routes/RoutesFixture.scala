package com.github.pandafolks.panda.routes

import cats.effect.Resource
import com.github.pandafolks.panda.routes.entity.{Mapper, Prefix}
import monix.connect.mongodb.client.{CollectionCodecRef, CollectionOperator, MongoConnection}
import monix.eval.Task
import org.mongodb.scala.{ConnectionString, MongoClientSettings}
import org.scalacheck.Gen
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName

trait RoutesFixture {
  protected val dbName: String = randomString("test")
  protected val mongoContainer: MongoDBContainer = new MongoDBContainer(
    DockerImageName.parse("mongo").withTag("4.0.10")
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

  protected val mappersColName: String = randomString(Mapper.MAPPERS_COLLECTION_NAME)
  protected val prefixesColName: String = randomString(Prefix.PREFIXES_COLLECTION_NAME)

  private val mappersCol: CollectionCodecRef[Mapper] = Mapper.getCollection(dbName, mappersColName)
  private val prefixesCol: CollectionCodecRef[Prefix] = Prefix.getCollection(dbName, prefixesColName)

  protected val mappersAndPrefixesConnection: Resource[Task, (CollectionOperator[Mapper], CollectionOperator[Prefix])] =
    MongoConnection.create2(settings, (mappersCol, prefixesCol))

  protected val mapperDao = new MapperDaoImpl()
  protected val prefixDao = new PrefixDaoImpl()

  protected val routesService =
    new RoutesServiceImpl(mapperDao, prefixDao)(mappersAndPrefixesConnection)(0, 0L) // cache turned off

  def randomString(prefix: String): String = Gen.uuid.map(prefix + _.toString.take(15)).sample.get

}
