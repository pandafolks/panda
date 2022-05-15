package com.github.mattszm.panda.db

import cats.effect.Resource
import com.github.mattszm.panda.db.MongoAppClient.{PARTICIPANT_EVENTS_COLLECTION_NAME, SEQUENCE_COLLECTION_NAME, TOKENS_COLLECTION_NAME, USERS_COLLECTION_NAME}
import com.github.mattszm.panda.participant.event.{ParticipantEvent, ParticipantEventDataModification, ParticipantEventType}
import com.github.mattszm.panda.sequence.{Sequence, SequenceKey}
import com.github.mattszm.panda.user.User
import com.github.mattszm.panda.user.token.Token
import monix.connect.mongodb.client.{CollectionCodecRef, CollectionOperator, MongoConnection}
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.bson.UuidRepresentation
import org.bson.codecs.UuidCodec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.codecs.Macros.createCodecProvider
import org.mongodb.scala.model.{IndexModel, IndexOptions, Indexes}
import org.mongodb.scala.{MongoClient, MongoClientSettings, MongoCredential, MongoDatabase, ServerAddress}

import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._

final class MongoAppClient(config: DbConfig) extends DbAppClient {

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

  private val participantEventsCol = CollectionCodecRef(config.dbName, PARTICIPANT_EVENTS_COLLECTION_NAME,
    classOf[ParticipantEvent],
    fromRegistries(fromProviders(
      classOf[ParticipantEvent],
      classOf[ParticipantEventDataModification],
      classOf[ParticipantEventType]
    ), javaCodecs, DEFAULT_CODEC_REGISTRY)
  )

  private val sequenceCol = CollectionCodecRef(config.dbName, SEQUENCE_COLLECTION_NAME,
    classOf[Sequence],
    fromRegistries(fromProviders(
      classOf[Sequence],
      classOf[SequenceKey]
    ), javaCodecs, DEFAULT_CODEC_REGISTRY)
  )

  private val tokensCol = CollectionCodecRef(config.dbName, TOKENS_COLLECTION_NAME, classOf[Token],
    fromRegistries(fromProviders(classOf[Token]), javaCodecs)
  )

  private val usersConnection = MongoConnection.create1(settings, usersCol)
  private val tokensConnection = MongoConnection.create1(settings, tokensCol)
  private val participantEventsAndSequencesConnection = MongoConnection.create2(settings, (participantEventsCol, sequenceCol))

  override def getUsersConnection: Resource[Task, CollectionOperator[User]] = usersConnection

  override def getTokensConnection: Resource[Task, CollectionOperator[Token]] = tokensConnection

  override def getParticipantEventsAndSequencesConnection: Resource[Task, (CollectionOperator[ParticipantEvent], CollectionOperator[Sequence])] = participantEventsAndSequencesConnection

  locally {
    //    creating indexes
    val mongoClient: MongoClient = MongoClient(settings)
    val database: MongoDatabase = mongoClient.getDatabase(config.dbName)

    (
      Task.fromReactivePublisher(
        database.getCollection(USERS_COLLECTION_NAME).createIndexes(
          Seq(
            IndexModel(
              Indexes.ascending("username"),
              IndexOptions().background(true).unique(false)
            )
          )
        )
      ) >>
        Task.fromReactivePublisher(
          database.getCollection(PARTICIPANT_EVENTS_COLLECTION_NAME).createIndexes(
            Seq(
              IndexModel(
                Indexes.compoundIndex(
                  Indexes.ascending("participantIdentifier"),
                  Indexes.descending("eventId")
                ),
                IndexOptions().background(false).unique(true)
              )
            )
          )
        ) >>
        Task.fromReactivePublisher(
          database.getCollection(TOKENS_COLLECTION_NAME).createIndexes(
            Seq(
              IndexModel(
                Indexes.hashed("tempId"),
                IndexOptions().background(false).unique(false)
              )
            )
          )
        )
      ).runSyncUnsafe(10.seconds)
    mongoClient.close()
  }
}

object MongoAppClient {
  final val USERS_COLLECTION_NAME = "users"
  final val PARTICIPANT_EVENTS_COLLECTION_NAME = "participant_events"
  final val SEQUENCE_COLLECTION_NAME = "sequence_generator"
  final val TOKENS_COLLECTION_NAME = "tokens"
}
