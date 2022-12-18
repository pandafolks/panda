package com.github.pandafolks.panda.nodestracker

import cats.effect.Resource
import com.github.pandafolks.panda.utils.{PersistenceError, UnsuccessfulUpdateOperation}
import monix.connect.mongodb.client.CollectionOperator
import monix.connect.mongodb.domain.RetryStrategy
import monix.eval.Task
import org.bson.types.ObjectId
import org.mongodb.scala.model.{Filters, UpdateOptions, Updates}

final class JobDaoImpl(private val c: Resource[Task, CollectionOperator[Job]]) extends JobDao {

  override def find(jobName: String): Task[Option[Job]] = c.use { op =>
    op.source
      .find(Filters.eq(Job.NAME_PROPERTY_NAME, jobName))
      .firstOptionL
      .onErrorRecoverWith { _: Throwable => Task.now(Option.empty) }
  }

  override def assignNodeToJob(jobName: String, nodeId: ObjectId): Task[Either[PersistenceError, Unit]] = c.use { op =>
    op.single
      .updateOne(
        filter = Filters.eq(Job.NAME_PROPERTY_NAME, jobName),
        update = Updates.set(Job.NODE_ID_PROPERTY_NAME, nodeId),
        updateOptions = UpdateOptions().upsert(true),
        retryStrategy = RetryStrategy(3)
      )
      .map(_ => Right(()))
      .onErrorRecoverWith { case t: Throwable => Task.now(Left(UnsuccessfulUpdateOperation(t.getMessage))) }
  }
}
