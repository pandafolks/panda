package com.github.pandafolks.panda.healthcheck

import cats.effect.Resource
import com.github.pandafolks.panda.utils.{PersistenceError, UnsuccessfulDeleteOperation, UnsuccessfulUpdateOperation}
import com.mongodb.client.model.{Filters, ReturnDocument}
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task
import org.mongodb.scala.model.{FindOneAndUpdateOptions, Updates}
import org.slf4j.LoggerFactory

final class UnsuccessfulHealthCheckDaoImpl(private val c: Resource[Task, CollectionOperator[UnsuccessfulHealthCheck]])
    extends UnsuccessfulHealthCheckDao {

  private val logger = LoggerFactory.getLogger(getClass.getName)

  override def incrementCounter(identifier: String): Task[Either[PersistenceError, Long]] = c.use { op =>
    val filter = Filters.eq(UnsuccessfulHealthCheck.IDENTIFIER_PROPERTY_NAME, identifier)
    val update = Updates.combine(
      Updates.inc(UnsuccessfulHealthCheck.COUNTER_PROPERTY_NAME, 1L),
      Updates.set(UnsuccessfulHealthCheck.LAST_UPDATE_TIMESTAMP_PROPERTY_NAME, System.currentTimeMillis()),
      Updates.setOnInsert(UnsuccessfulHealthCheck.TURNED_OFF_PROPERTY_NAME, false)
    )
    val options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).upsert(true)

    op.source
      .findOneAndUpdate(
        filter = filter,
        update = update,
        findOneAndUpdateOptions = options
      )
      .map(_.map(_.counter))
      .map(_.toRight(UnsuccessfulUpdateOperation("No update performed")))
  }

  override def clear(identifier: String): Task[Either[PersistenceError, Unit]] = c.use(op =>
    op.single
      .deleteOne(Filters.eq(UnsuccessfulHealthCheck.IDENTIFIER_PROPERTY_NAME, identifier))
      .map(_ => Right(()))
      .onErrorRecoverWith { t: Throwable => Task.now(Left(UnsuccessfulDeleteOperation(t.getMessage))) }
  )

  override def markAsTurnedOff(identifiers: List[String]): Task[Either[PersistenceError, Unit]] = c.use { op =>
    val filter = Filters.in(UnsuccessfulHealthCheck.IDENTIFIER_PROPERTY_NAME, identifiers.toSet.toSeq: _*)
    val update = Updates.set(UnsuccessfulHealthCheck.TURNED_OFF_PROPERTY_NAME, true)

    op.single
      .updateMany(
        filter = filter,
        update = update
      )
      .map(_ => Right(()))
      .onErrorRecoverWith { t: Throwable => Task.now(Left(UnsuccessfulUpdateOperation(t.getMessage))) }
  }

  override def getStaleEntries(deviation: Long, minimumFailedCounters: Int): Task[List[UnsuccessfulHealthCheck]] =
    c.use { op =>
      val filter = Filters.and(
        Filters
          .lte(UnsuccessfulHealthCheck.LAST_UPDATE_TIMESTAMP_PROPERTY_NAME, System.currentTimeMillis() - deviation),
        Filters.gte(UnsuccessfulHealthCheck.COUNTER_PROPERTY_NAME, minimumFailedCounters)
      )

      // todo: mszmal start here and add tests!

      op.source
        .find(filter)
        .toListL
        .onErrorRecover(e => {
          logger.error("Unable to get stale entries", e)
          List.empty
        })
    }
}
