package com.github.pandafolks.panda.db

import cats.effect.Resource
import com.github.pandafolks.panda.healthcheck.UnsuccessfulHealthCheck
import com.github.pandafolks.panda.healthcheck.UnsuccessfulHealthCheck.UNSUCCESSFUL_HEALTH_CHECK_COLLECTION_NAME
import com.github.pandafolks.panda.participant.event.ParticipantEvent
import com.github.pandafolks.panda.participant.event.ParticipantEvent.PARTICIPANT_EVENTS_COLLECTION_NAME
import com.github.pandafolks.panda.routes.entity.Mapper.MAPPERS_COLLECTION_NAME
import com.github.pandafolks.panda.routes.entity.Prefix.PREFIXES_COLLECTION_NAME
import com.github.pandafolks.panda.routes.entity.{Mapper, Prefix}
import com.github.pandafolks.panda.user.User
import com.github.pandafolks.panda.user.User.USERS_COLLECTION_NAME
import com.github.pandafolks.panda.user.token.Token
import com.github.pandafolks.panda.user.token.Token.TOKENS_COLLECTION_NAME
import com.github.pandafolks.panda.utils.PandaStartupException
import com.mongodb.{ConnectionString, ReadConcern, ReadPreference, WriteConcern}
import com.mongodb.connection.ClusterConnectionMode
import com.pandafolks.mattszm.panda.sequence.Sequence
import monix.connect.mongodb.client.{CollectionCodecRef, CollectionOperator, MongoConnection}
import monix.eval.Task
import com.github.pandafolks.panda.utils.scheduler.CoreScheduler.scheduler
import org.mongodb.scala.model.{IndexModel, IndexOptions, Indexes}
import org.mongodb.scala.{MongoClient, MongoClientSettings, MongoCredential, MongoDatabase, ServerAddress}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._

final class MongoAppClient(config: DbConfig) extends DbAppClient {

  final val baseSettings =
    if (config.connectionString.isDefined)
      MongoClientSettings.builder().applyConnectionString(new ConnectionString(config.connectionString.get))
    else MongoClientSettings.builder()
      .credential(MongoCredential.createCredential(config.username.get, config.dbName, config.password.get.toCharArray))
      .applyToClusterSettings(builder => {
        builder
          .hosts(config.contactPoints.get.map(cp => new ServerAddress(cp.host, cp.port)).asJava)
          .mode(config.mode.get.toLowerCase() match {
            case "multiple" => ClusterConnectionMode.MULTIPLE
            case "load_balanced" => throw new PandaStartupException("MongoDB LOAD_BALANCED cluster mode is not supported.")
            case _ => ClusterConnectionMode.SINGLE
          })
        ()
      })

   val settings: MongoClientSettings = baseSettings
    .readPreference(ReadPreference.nearest())
    .writeConcern(WriteConcern.MAJORITY)
    .readConcern(ReadConcern.MAJORITY)
    .retryReads(true)
    .retryWrites(true)
    .applyToServerSettings(builder => {
      builder
        .heartbeatFrequency(2000, TimeUnit.MILLISECONDS) // 2 seconds
      ()
    }).build()


  private val participantEventsCol: CollectionCodecRef[ParticipantEvent] = ParticipantEvent.getCollection(config.dbName)
  private val sequenceCol: CollectionCodecRef[Sequence] = Sequence.getCollection(config.dbName) // todo mszmal: remove once participants migrated
  private val usersCol: CollectionCodecRef[User] = User.getCollection(config.dbName)
  private val tokensCol: CollectionCodecRef[Token] = Token.getCollection(config.dbName)
  private val unsuccessfulHealthCheckCol: CollectionCodecRef[UnsuccessfulHealthCheck] = UnsuccessfulHealthCheck.getCollection(config.dbName)
  private val mappersCol: CollectionCodecRef[Mapper] = Mapper.getCollection(config.dbName)
  private val prefixesCol: CollectionCodecRef[Prefix] = Prefix.getCollection(config.dbName)

  private val participantEventsAndSequencesConnection = MongoConnection.create2(settings, (participantEventsCol, sequenceCol))
  private val usersWithTokensConnection = MongoConnection.create2(settings, (usersCol, tokensCol))
  private val unsuccessfulHealthCheckConnection = MongoConnection.create1(settings, unsuccessfulHealthCheckCol)
  private val mappersAndPrefixesConnection = MongoConnection.create2(settings, (mappersCol, prefixesCol))

  override def getSettings: Any = settings


  override def getDbName: String = config.dbName

  override def getParticipantEventsAndSequencesConnection: Resource[Task, (CollectionOperator[ParticipantEvent], CollectionOperator[Sequence])] = participantEventsAndSequencesConnection

  override def getUsersWithTokensConnection: Resource[Task, (CollectionOperator[User], CollectionOperator[Token])] = usersWithTokensConnection

  override def getUnsuccessfulHealthCheckConnection: Resource[Task, CollectionOperator[UnsuccessfulHealthCheck]] = unsuccessfulHealthCheckConnection

  override def getMappersAndPrefixesConnection: Resource[Task, (CollectionOperator[Mapper], CollectionOperator[Prefix])] = mappersAndPrefixesConnection

  locally {
    //    creating indexes
    val tmpMongoClient: MongoClient = MongoClient(settings)
    val database: MongoDatabase = tmpMongoClient.getDatabase(config.dbName)

    (
      Task.fromReactivePublisher(
        database.getCollection(USERS_COLLECTION_NAME).createIndexes(
          Seq(
            IndexModel(
              Indexes.ascending("username"),
              IndexOptions().background(false).unique(true)
            ),
            IndexModel(
              Indexes.ascending("id"),
              IndexOptions().background(false).unique(true)
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
                IndexOptions().background(true).unique(true)
              ),
              IndexModel(
                Indexes.ascending("eventId"),
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
        ) >>
        Task.fromReactivePublisher(
          database.getCollection(UNSUCCESSFUL_HEALTH_CHECK_COLLECTION_NAME).createIndexes(
            Seq(
              IndexModel(
                Indexes.ascending("identifier"),
                IndexOptions().background(false).unique(true)
              )
            )
          )
        ) >>
        Task.fromReactivePublisher(
          database.getCollection(MAPPERS_COLLECTION_NAME).createIndexes(
            Seq(
              IndexModel(
                Indexes.ascending("lastUpdateTimestamp"),
                IndexOptions().background(false).unique(false)
              ),
              IndexModel(
                Indexes.compoundIndex(
                  Indexes.ascending("route"),
                  Indexes.ascending("httpMethod")
                ),
                IndexOptions().background(false).unique(true)
              )
            )
          )
        ) >>
        Task.fromReactivePublisher(
          database.getCollection(PREFIXES_COLLECTION_NAME).createIndexes(
            Seq(
              IndexModel(
                Indexes.ascending("groupName"),
                IndexOptions().background(false).unique(true)
              )
            )
          )
        )
      ).runSyncUnsafe(1.minutes)

    tmpMongoClient.close()
  }
}
