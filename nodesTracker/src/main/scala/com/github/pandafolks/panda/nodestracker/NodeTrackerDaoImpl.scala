package com.github.pandafolks.panda.nodestracker

import cats.effect.Resource
import com.github.pandafolks.panda.utils.{
  NotExists,
  PersistenceError,
  UnsuccessfulSaveOperation,
  UnsuccessfulUpdateOperation
}
import monix.connect.mongodb.client.CollectionOperator
import monix.connect.mongodb.domain.{RetryStrategy, UpdateResult}
import monix.eval.Task
import org.bson.types.ObjectId
import org.mongodb.scala.model.{Aggregates, Filters, Sorts, UpdateOptions, Updates}

final class NodeTrackerDaoImpl(private val c: Resource[Task, CollectionOperator[Node]]) extends NodeTrackerDao {

  override def register(): Task[Either[PersistenceError, String]] = c.use(nodeOperator =>
    nodeOperator.single
      .insertOne(
        document = Node(new ObjectId(), System.currentTimeMillis()),
        retryStrategy = RetryStrategy(5)
      )
      .map(_.insertedId)
      .map {
        case Some(id) => Right(id)
        case None     => Left(UnsuccessfulSaveOperation("Cannot obtain Node ID"))
      }
      .onErrorRecoverWith { case t: Throwable => Task.now(Left(UnsuccessfulSaveOperation(t.getMessage))) }
  )

  override def notify(nodeId: String): Task[Either[PersistenceError, Unit]] = c.use(nodeOperator =>
    nodeOperator.single
      .updateOne(
        filter = Filters.eq(Node.ID_PROPERTY_NAME, new ObjectId(nodeId)),
        update = Updates.set(Node.LAST_UPDATE_TIMESTAMP_PROPERTY_NAME, System.currentTimeMillis()),
        updateOptions = UpdateOptions().upsert(true),
        retryStrategy = RetryStrategy(3)
      )
      .map {
        case UpdateResult(_, modifiedCount, _) if modifiedCount > 0 => Right(())
        case _ => Left(NotExists("There is no instance with the requested ID in the Node Tracker."))
      }
      .onErrorRecoverWith { case t: Throwable => Task.now(Left(UnsuccessfulUpdateOperation(t.getMessage))) }
  )

  override def getNodes(deviation: Long): Task[List[Node]] = c.use { nodeOperator =>
    nodeOperator.source
      .aggregate(
        List(
          Aggregates.filter(Filters.gte(Node.LAST_UPDATE_TIMESTAMP_PROPERTY_NAME, System.currentTimeMillis() - deviation)),
          Aggregates.sort(Sorts.ascending(Node.ID_PROPERTY_NAME))
        ),
        classOf[Node]
      )
      .toListL
      .onErrorRecover(_ => List.empty)
  }

  override def isNodeWorking(nodeId: ObjectId, deviation: Long): Task[Boolean] = c.use { nodeOperator =>
    nodeOperator.source
      .count(
        filter = Filters.and(
          Filters.gte(Node.LAST_UPDATE_TIMESTAMP_PROPERTY_NAME, System.currentTimeMillis() - deviation),
          Filters.eq(Node.ID_PROPERTY_NAME, nodeId)
        )
      )
      .map(_ > 0)
      .onErrorRecover(_ => false)
  }
}
