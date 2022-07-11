package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.entity.Mapper.{HTTP_METHOD_PROPERTY_NAME, LAST_UPDATE_TIMESTAMP_PROPERTY_NAME, MAPPING_CONTENT_PROPERTY_NAME, ROUTE_PROPERTY_NAME}
import com.github.pandafolks.panda.routes.entity.{Mapper, MappingContent}
import com.github.pandafolks.panda.routes.payload.MapperRecordPayload
import com.github.pandafolks.panda.utils.{AlreadyExists, NotExists, PersistenceError, UnsuccessfulDeleteOperation, UnsuccessfulSaveOperation}
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task
import monix.reactive.Observable
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.{Filters, UpdateOptions, Updates}

final class MapperDaoImpl extends MapperDao {

  private final val clock = java.time.Clock.systemUTC

  override def saveMapper(route: String, mapperRecordDto: MapperRecordPayload)(
    mapperOperator: CollectionOperator[Mapper]): Task[Either[PersistenceError, String]] = {
    val unifiedHttpMethod = HttpMethod.unify(mapperRecordDto.method)
    mapperOperator.single.updateOne(
      getUniqueMapperFilter(route, unifiedHttpMethod),
      Updates.combine(
        Updates.setOnInsert(MAPPING_CONTENT_PROPERTY_NAME, MappingContent.fromMappingPayload(mapperRecordDto.mapping)),
        Updates.setOnInsert(LAST_UPDATE_TIMESTAMP_PROPERTY_NAME, clock.millis())
      ),
      updateOptions = UpdateOptions().upsert(true)
    ).map { updateRes =>
      if (updateRes.matchedCount == 0) Right(route)
      else Left(AlreadyExists(s"Route \'$route\' [$unifiedHttpMethod] already exists"))
    }
      .onErrorRecoverWith { case t: Throwable => Task.now(Left(UnsuccessfulSaveOperation(t.getMessage))) }
  }

  override def saveOrUpdateMapper(route: String, mapperRecordDto: MapperRecordPayload)(
    mapperOperator: CollectionOperator[Mapper]): Task[Either[PersistenceError, String]] = {
    val unifiedHttpMethod = HttpMethod.unify(mapperRecordDto.method)
    mapperOperator.single.updateOne(
      getUniqueMapperFilter(route, unifiedHttpMethod),
      Updates.combine(
        Updates.set(MAPPING_CONTENT_PROPERTY_NAME, MappingContent.fromMappingPayload(mapperRecordDto.mapping)),
        Updates.set(LAST_UPDATE_TIMESTAMP_PROPERTY_NAME, clock.millis())
      ),
      updateOptions = UpdateOptions().upsert(true)
    ).map(_ => Right(route))
      .onErrorRecoverWith { case t: Throwable => Task.now(Left(UnsuccessfulSaveOperation(t.getMessage))) }
  }

  override def findAll(mapperOperator: CollectionOperator[Mapper]): Observable[Mapper] = mapperOperator.source.findAll

  override def delete(route: String, method: Option[String])(mapperOperator: CollectionOperator[Mapper]): Task[Either[PersistenceError, String]] = {
    val unifiedHttpMethod = HttpMethod.unify(method)
    mapperOperator.single.deleteMany(getUniqueMapperFilter(route, unifiedHttpMethod))
      .map { deleteRes =>
        if (deleteRes.deleteCount > 0) Right(route) else Left(NotExists(s"Route \'$route\' [$unifiedHttpMethod] does not exist"))
      }
      .onErrorRecoverWith { case t: Throwable => Task.now(Left(UnsuccessfulDeleteOperation(t.getMessage))) }
  }

  private def getUniqueMapperFilter(route: String, method: String): Bson =
    Filters.and(
      Filters.eq(ROUTE_PROPERTY_NAME, route),
      Filters.eq(HTTP_METHOD_PROPERTY_NAME, method)
    )
}
