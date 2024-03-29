package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.entity.Mapper.{
  HTTP_METHOD_PROPERTY_NAME,
  IS_STANDALONE_PROPERTY_NAME,
  LAST_UPDATE_TIMESTAMP_PROPERTY_NAME,
  MAPPING_CONTENT_PROPERTY_NAME,
  ROUTE_PROPERTY_NAME
}
import com.github.pandafolks.panda.routes.entity.{Mapper, MappingContent}
import com.github.pandafolks.panda.routes.payload.MapperRecordPayload
import com.github.pandafolks.panda.utils.EscapeUtils.unifyFromSlashes
import com.github.pandafolks.panda.utils.{
  AlreadyExists,
  NotExists,
  PersistenceError,
  UnsuccessfulDeleteOperation,
  UnsuccessfulSaveOperation
}
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task
import monix.reactive.Observable
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.{Aggregates, Filters, UpdateOptions, Updates}

final class MapperDaoImpl extends MapperDao {

  private final val clock = java.time.Clock.systemUTC

  override def saveMapper(route: String, mapperRecordDto: MapperRecordPayload)(
      mapperOperator: CollectionOperator[Mapper]
  ): Task[Either[PersistenceError, String]] = {
    val unifiedHttpMethod = HttpMethod.getByName(mapperRecordDto.method)
    val unifiedRoute = unifyFromSlashes(route)
    mapperOperator.single
      .updateOne(
        getUniqueMapperFilter(unifiedRoute, unifiedHttpMethod),
        Updates.combine(
          Updates
            .setOnInsert(MAPPING_CONTENT_PROPERTY_NAME, MappingContent.fromMappingPayload(mapperRecordDto.mapping)),
          Updates.setOnInsert(IS_STANDALONE_PROPERTY_NAME, mapperRecordDto.isStandaloneOrDefault),
          Updates.setOnInsert(LAST_UPDATE_TIMESTAMP_PROPERTY_NAME, clock.millis())
        ),
        updateOptions = UpdateOptions().upsert(true)
      )
      .map { updateRes =>
        if (updateRes.matchedCount == 0) Right(unifiedRoute)
        else Left(AlreadyExists(s"Route \'$route\' [${unifiedHttpMethod.getName}] already exists"))
      }
      .onErrorRecoverWith { case t: Throwable => Task.now(Left(UnsuccessfulSaveOperation(t.getMessage))) }
  }

  override def saveOrUpdateMapper(route: String, mapperRecordDto: MapperRecordPayload)(
      mapperOperator: CollectionOperator[Mapper]
  ): Task[Either[PersistenceError, String]] = {
    val unifiedHttpMethod = HttpMethod.getByName(mapperRecordDto.method)
    val unifiedRoute = unifyFromSlashes(route)
    mapperOperator.single
      .updateOne(
        getUniqueMapperFilter(unifiedRoute, unifiedHttpMethod),
        Updates.combine(
          Updates.set(MAPPING_CONTENT_PROPERTY_NAME, MappingContent.fromMappingPayload(mapperRecordDto.mapping)),
          Updates.set(IS_STANDALONE_PROPERTY_NAME, mapperRecordDto.isStandaloneOrDefault),
          Updates.set(LAST_UPDATE_TIMESTAMP_PROPERTY_NAME, clock.millis())
        ),
        updateOptions = UpdateOptions().upsert(true)
      )
      .map(_ => Right(unifiedRoute))
      .onErrorRecoverWith { case t: Throwable => Task.now(Left(UnsuccessfulSaveOperation(t.getMessage))) }
  }

  override def findAll(mapperOperator: CollectionOperator[Mapper]): Observable[Mapper] = mapperOperator.source.findAll

  override def delete(route: String, method: Option[String])(
      mapperOperator: CollectionOperator[Mapper]
  ): Task[Either[PersistenceError, String]] = {
    val unifiedHttpMethod = HttpMethod.getByName(method)
    val unifiedRoute = unifyFromSlashes(route)
    mapperOperator.single
      .deleteMany(getUniqueMapperFilter(unifiedRoute, unifiedHttpMethod))
      .map { deleteRes =>
        if (deleteRes.deleteCount > 0) Right(unifiedRoute)
        else Left(NotExists(s"Route \'$route\' [${unifiedHttpMethod.getName}] does not exist"))
      }
      .onErrorRecoverWith { case t: Throwable => Task.now(Left(UnsuccessfulDeleteOperation(t.getMessage))) }
  }

  override def checkIfThereAreNewerMappings(
      timeStamp: Long
  )(mapperOperator: CollectionOperator[Mapper]): Task[Boolean] =
    mapperOperator.source
      .aggregate(
        List(
          Aggregates.filter(Filters.gt(Mapper.LAST_UPDATE_TIMESTAMP_PROPERTY_NAME, timeStamp)),
          Aggregates.limit(1)
        ),
        classOf[Mapper]
      )
      .nonEmptyL

  private def getUniqueMapperFilter(route: String, method: HttpMethod): Bson =
    Filters.and(
      Filters.eq(ROUTE_PROPERTY_NAME, route),
      Filters.eq(HTTP_METHOD_PROPERTY_NAME, method)
    )

}
