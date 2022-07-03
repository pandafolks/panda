package com.github.pandafolks.panda.routes

import cats.effect.Resource
import com.github.pandafolks.panda.routes.entity.{Mapper, MappingContent, Prefix}
import com.github.pandafolks.panda.routes.payload.{MapperRecordPayload, RoutesRemovePayload, RoutesResourcePayload}
import com.github.pandafolks.panda.utils.PersistenceError
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task

final class RoutesServiceImpl(private val mapperDao: MapperDao, private val prefixesDao: PrefixesDao)(
  private val c: Resource[Task, (CollectionOperator[Mapper], CollectionOperator[Prefix])]) extends RoutesService {

  override def findAll(): Task[RoutesResourcePayload] = c.use {
    case (mapperOperator, prefixesOperator) =>
      Task.parMap2(
        mapperDao.findAll(mapperOperator)
          .map(mapper => (mapper.route, mapper.httpMethod, MappingContent.toMappingPayload(mapper.mappingContent)))
          .foldLeftL(Map.empty[String, MapperRecordPayload])((prevState, p) => prevState + (p._1 -> MapperRecordPayload(p._3, Some(p._2)))),

        prefixesDao.findAll(prefixesOperator)
          .map(prefix => (prefix.groupName, prefix.value))
          .foldLeftL(Map.empty[String, String])((prevState, p) => prevState + p)
      )((mappers, prefixes) => RoutesResourcePayload(mappers = Some(mappers), prefixes = Some(prefixes)))
  }

  override def save(routesResourcePayload: RoutesResourcePayload): Task[(List[Either[PersistenceError, String]], List[Either[PersistenceError, String]])] =
    c.use {
      case (mapperOperator, prefixesOperator) =>
        Task.parZip2(
          Task.parTraverse(routesResourcePayload.mappers.getOrElse(Map.empty).toList) { entry =>
            mapperDao.saveMapper(entry._1, entry._2)(mapperOperator)
          },
          Task.parTraverse(routesResourcePayload.prefixes.getOrElse(Map.empty).toList) { entry =>
            prefixesDao.savePrefix(entry._1, entry._2)(prefixesOperator)
          }
        )
    }

  override def delete(routesRemovePayload: RoutesRemovePayload): Task[(List[Either[PersistenceError, String]], List[Either[PersistenceError, String]])] =
    c.use {
      case (mapperOperator, prefixesOperator) =>
        Task.parZip2(
          Task.parTraverse(routesRemovePayload.mappers.getOrElse(List.empty)) { item =>
            mapperDao.delete(item.route, item.method)(mapperOperator)
          },
          Task.parTraverse(routesRemovePayload.prefixes.getOrElse(List.empty)) { item =>
            prefixesDao.delete(item)(prefixesOperator)
          }
        )
    }

}