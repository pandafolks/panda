package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.entity.Prefix
import com.github.pandafolks.panda.routes.entity.Prefix.{GROUP_NAME_PROPERTY_NAME, LAST_UPDATE_TIMESTAMP_PROPERTY_NAME, VALUE_PROPERTY_NAME}
import com.github.pandafolks.panda.utils.EscapeUtils.unifyFromSlashes
import com.github.pandafolks.panda.utils.{AlreadyExists, NotExists, PersistenceError, UnsuccessfulDeleteOperation, UnsuccessfulSaveOperation}
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task
import monix.reactive.Observable
import org.mongodb.scala.model.{Filters, UpdateOptions, Updates}

final class PrefixDaoImpl extends PrefixDao {

  private final val clock = java.time.Clock.systemUTC

  override def savePrefix(groupName: String, prefix: String)(
    prefixOperator: CollectionOperator[Prefix]): Task[Either[PersistenceError, String]] =
    prefixOperator.single.updateOne(
      Filters.eq(GROUP_NAME_PROPERTY_NAME, groupName),
      Updates.combine(
        Updates.setOnInsert(VALUE_PROPERTY_NAME, unifyFromSlashes(prefix)),
        Updates.setOnInsert(LAST_UPDATE_TIMESTAMP_PROPERTY_NAME, clock.millis())
      ),
      updateOptions = UpdateOptions().upsert(true)
    ).map { updateRes =>
      if (updateRes.matchedCount == 0) Right(groupName)
      else Left(AlreadyExists(s"Group \'$groupName\' has already defined prefix"))
    }
      .onErrorRecoverWith { case t: Throwable => Task.now(Left(UnsuccessfulSaveOperation(t.getMessage))) }

  override def saveOrUpdatePrefix(groupName: String, prefix: String)(
    prefixOperator: CollectionOperator[Prefix]): Task[Either[PersistenceError, String]] =
    prefixOperator.single.updateOne(
      Filters.eq(GROUP_NAME_PROPERTY_NAME, groupName),
      Updates.combine(
        Updates.set(VALUE_PROPERTY_NAME, unifyFromSlashes(prefix)),
        Updates.set(LAST_UPDATE_TIMESTAMP_PROPERTY_NAME, clock.millis())
      ),
      updateOptions = UpdateOptions().upsert(true)
    ).map(_ => Right(groupName))
      .onErrorRecoverWith { case t: Throwable => Task.now(Left(UnsuccessfulSaveOperation(t.getMessage))) }

  override def findAll(prefixOperator: CollectionOperator[Prefix]): Observable[Prefix] = prefixOperator.source.findAll

  override def delete(groupName: String)(prefixOperator: CollectionOperator[Prefix]): Task[Either[PersistenceError, String]] =
    prefixOperator.single.deleteMany(Filters.eq(GROUP_NAME_PROPERTY_NAME, groupName))
      .map { deleteRes =>
        if (deleteRes.deleteCount > 0) Right(groupName) else Left(NotExists(s"There is no prefix associated with the group \'$groupName\'"))
      }
      .onErrorRecoverWith { case t: Throwable => Task.now(Left(UnsuccessfulDeleteOperation(t.getMessage))) }
}
