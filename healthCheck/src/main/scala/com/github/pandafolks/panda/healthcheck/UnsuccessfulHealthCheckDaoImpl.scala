package com.github.pandafolks.panda.healthcheck

import cats.effect.Resource
import com.github.pandafolks.panda.utils.{PersistenceError, UnsuccessfulDeleteOperation, UnsuccessfulUpdateOperation}
import com.mongodb.client.model.{Filters, ReturnDocument}
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task
import org.mongodb.scala.model.{FindOneAndUpdateOptions, Updates}

final class UnsuccessfulHealthCheckDaoImpl(private val c: Resource[Task, CollectionOperator[UnsuccessfulHealthCheck]]
                                          ) extends UnsuccessfulHealthCheckDao {

  override def incrementCounter(identifier: String): Task[Either[PersistenceError, Long]] = c.use { op =>
    val filter = Filters.eq("identifier", identifier)
    val update = Updates.inc("counter", 1L)
    val options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).upsert(true)

    op.source.findOneAndUpdate(
      filter = filter,
      update = update,
      findOneAndUpdateOptions = options
    ).map(_.map(_.counter))
      .map(_.toRight(UnsuccessfulUpdateOperation("No update performed")))
  }

  override def clear(identifier: String): Task[Either[PersistenceError, Unit]] = c.use(op =>
    op.single.deleteOne(Filters.eq("identifier", identifier))
      .map(_ => Right(()))
      .onErrorRecoverWith { t: Throwable => Task.now(Left(UnsuccessfulDeleteOperation(t.getMessage))) }
  )
}
