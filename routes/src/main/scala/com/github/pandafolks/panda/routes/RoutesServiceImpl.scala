package com.github.pandafolks.panda.routes

import cats.effect.Resource
import com.github.pandafolks.panda.routes.dto.RoutesResourceDto
import com.github.pandafolks.panda.routes.mappers.{Mapper, Prefix}
import com.github.pandafolks.panda.utils.PersistenceError
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task

final class RoutesServiceImpl(private val routesDto: RoutesDao)(
  private val c: Resource[Task, (CollectionOperator[Mapper], CollectionOperator[Prefix])]) extends RoutesService {

  override def saveRoutes(routesResourceDto: RoutesResourceDto): Task[List[Either[PersistenceError, String]]] = c.use {
    case (mapperOperator, _) =>
      Task.parTraverse(routesResourceDto.mappers.getOrElse(Map.empty).toList) { entry =>
        routesDto.saveRoute(entry._1, entry._2)(mapperOperator)
      }
      // todo mszmal: handle prefixes
  }
}
