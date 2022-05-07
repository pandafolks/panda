package com.github.mattszm.panda.db

import cats.effect.Resource
import com.github.mattszm.panda.bootstrap.configuration.sub.DbConfig
import com.github.mattszm.panda.db.MongoAppClient.USERS_COLLECTION_NAME
import com.github.mattszm.panda.user.User
import monix.connect.mongodb.client.{CollectionCodecRef, CollectionOperator, MongoConnection}
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.bson.UuidRepresentation
import org.bson.codecs.UuidCodec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.bson.codecs.Macros.createCodecProvider
import org.mongodb.scala.model.{IndexModel, IndexOptions, Indexes}
import org.mongodb.scala.{MongoClient, MongoClientSettings, MongoCredential, MongoDatabase, ServerAddress}

import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._

final class MongoAppClient(config: DbConfig) {
  private val settings: MongoClientSettings =
    MongoClientSettings.builder()
      .credential(MongoCredential.createCredential(config.username, config.dbName, config.password.toCharArray))
      .applyToClusterSettings(builder => {
        builder.hosts(
          config.contactPoints.map(cp => new ServerAddress(cp.host, cp.port)).asJava
        )
        ()
      }
      ).build()

  private val javaCodecs = CodecRegistries.fromCodecs(
    new UuidCodec(UuidRepresentation.STANDARD)
  )

  private val usersCol = CollectionCodecRef(config.dbName, USERS_COLLECTION_NAME, classOf[User],
    fromRegistries(fromProviders(classOf[User]), javaCodecs)
  )

  private val connection: Resource[Task, CollectionOperator[User]] = MongoConnection.create1(settings, usersCol)

  def getConnection: Resource[Task, CollectionOperator[User]] = connection

  locally {
    //    creating indexes
    val mongoClient: MongoClient = MongoClient(settings)
    val database: MongoDatabase = mongoClient.getDatabase(config.dbName)

    Task.fromReactivePublisher(database.getCollection(USERS_COLLECTION_NAME).createIndexes(
      Seq(
        IndexModel(
          Indexes.ascending("username"),
          IndexOptions().background(true).unique(false)
        )
      )
    )).runSyncUnsafe(10.seconds)
    mongoClient.close()
  }
}

object MongoAppClient {
  final val USERS_COLLECTION_NAME = "users"
}
