package com.github.pandafolks.panda.db

import cats.effect.Resource
import com.github.pandafolks.panda.healthcheck.UnsuccessfulHealthCheck
import com.github.pandafolks.panda.healthcheck.UnsuccessfulHealthCheck.UNSUCCESSFUL_HEALTH_CHECK_COLLECTION_NAME
import com.github.pandafolks.panda.nodestracker.Node
import com.github.pandafolks.panda.nodestracker.Node.NODES_COLLECTION_NAME
import com.github.pandafolks.panda.participant.event.ParticipantEvent
import com.github.pandafolks.panda.participant.event.ParticipantEvent.PARTICIPANT_EVENTS_COLLECTION_NAME
import com.github.pandafolks.panda.user.User
import com.github.pandafolks.panda.user.User.USERS_COLLECTION_NAME
import com.github.pandafolks.panda.user.token.Token
import com.github.pandafolks.panda.user.token.Token.TOKENS_COLLECTION_NAME
import com.mongodb.connection.ClusterConnectionMode
import com.pandafolks.mattszm.panda.sequence.Sequence
import monix.connect.mongodb.client.{CollectionCodecRef, CollectionOperator, MongoConnection}
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.mongodb.scala.model.{IndexModel, IndexOptions, Indexes}
import org.mongodb.scala.{MongoClient, MongoClientSettings, MongoCredential, MongoDatabase, ServerAddress}

import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._

final class MongoAppClient(config: DbConfig) extends DbAppClient {

  private val settings: MongoClientSettings =
    MongoClientSettings.builder()
      .credential(MongoCredential.createCredential(config.username, config.dbName, config.password.toCharArray))
      .applyToClusterSettings(builder => {
        builder
          .hosts(config.contactPoints.map(cp => new ServerAddress(cp.host, cp.port)).asJava)
          .mode(config.mode.toLowerCase() match {
            case "multiple" => ClusterConnectionMode.MULTIPLE
            case "load_balanced" => ClusterConnectionMode.LOAD_BALANCED
            case _ => ClusterConnectionMode.SINGLE
          })
        ()
      }
      ).build()

  private val participantEventsCol: CollectionCodecRef[ParticipantEvent] = ParticipantEvent.getCollection(config.dbName)
  private val sequenceCol: CollectionCodecRef[Sequence] = Sequence.getCollection(config.dbName)
  private val usersCol: CollectionCodecRef[User] = User.getCollection(config.dbName)
  private val tokensCol: CollectionCodecRef[Token] = Token.getCollection(config.dbName)
  private val nodesCol: CollectionCodecRef[Node] = Node.getCollection(config.dbName)
  private val unsuccessfulHealthCheckCol: CollectionCodecRef[UnsuccessfulHealthCheck] = UnsuccessfulHealthCheck.getCollection(config.dbName)

  private val participantEventsAndSequencesConnection = MongoConnection.create2(settings, (participantEventsCol, sequenceCol))
  private val usersWithTokensConnection = MongoConnection.create2(settings, (usersCol, tokensCol))
  private val nodesConnection = MongoConnection.create1(settings, nodesCol)
  private val unsuccessfulHealthCheckConnection = MongoConnection.create1(settings, unsuccessfulHealthCheckCol)

  override def getParticipantEventsAndSequencesConnection: Resource[Task, (CollectionOperator[ParticipantEvent], CollectionOperator[Sequence])] = participantEventsAndSequencesConnection

  override def getUsersWithTokensConnection: Resource[Task, (CollectionOperator[User], CollectionOperator[Token])] = usersWithTokensConnection

  override def getNodesConnection: Resource[Task, CollectionOperator[Node]] = nodesConnection

  override def getUnsuccessfulHealthCheckConnection: Resource[Task, CollectionOperator[UnsuccessfulHealthCheck]] = unsuccessfulHealthCheckConnection

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
          database.getCollection(NODES_COLLECTION_NAME).createIndexes(
            Seq(
              IndexModel(
                Indexes.compoundIndex(
                  Indexes.ascending("lastUpdateTimestamp"),
                  Indexes.ascending("_id")
                ),
                IndexOptions().background(false).unique(false)
              ),
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
        )
      ).runSyncUnsafe(30.seconds)

    mongoClient.close()
  }
}
