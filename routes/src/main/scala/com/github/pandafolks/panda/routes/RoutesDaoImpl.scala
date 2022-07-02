package com.github.pandafolks.panda.routes
import com.github.pandafolks.panda.routes.dto.MapperRecordDto
import com.github.pandafolks.panda.routes.mappers.Mapper
import com.github.pandafolks.panda.utils.{AlreadyExists, PersistenceError, UnsuccessfulSaveOperation}
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task
import org.mongodb.scala.model.{Filters, UpdateOptions, Updates}

final class RoutesDaoImpl extends RoutesDao {

  private final val clock = java.time.Clock.systemUTC

  override def saveRoute(route: String, mapperRecordDto: MapperRecordDto)(
    mapperOperator: CollectionOperator[Mapper]): Task[Either[PersistenceError, String]] = {
    val unifiedHttpMethod = HttpMethod.unify(mapperRecordDto.method)
    mapperOperator.single.updateOne(
      Filters.and(
        Filters.eq("route", route),
        Filters.eq("httpMethod", unifiedHttpMethod)
      ),
      Updates.combine(
        Updates.setOnInsert("groupName", mapperRecordDto.groupName),
        Updates.setOnInsert("lastUpdateTimestamp", clock.millis())
      ),
      updateOptions = UpdateOptions().upsert(true)
    ).map { updateRes =>
      if (updateRes.matchedCount == 0) Right(route)
      else Left(AlreadyExists(s"Route \'$route\' [$unifiedHttpMethod] already exists"))
    }
      .onErrorRecoverWith { case t: Throwable => Task.now(Left(UnsuccessfulSaveOperation(t.getMessage))) }
  }
}
