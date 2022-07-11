package com.github.pandafolks.panda.routes

import cats.effect.Resource
import com.github.pandafolks.panda.routes.entity.{Mapper, MappingContent, Prefix}
import com.github.pandafolks.panda.routes.payload.{MapperRecordPayload, MappingPayload, RoutesRemovePayload, RoutesResourcePayload}
import com.github.pandafolks.panda.utils.PersistenceError
import com.github.pandafolks.panda.utils.cache.{CustomCache, CustomCacheImpl}
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task

import scala.concurrent.duration.DurationInt

final class RoutesServiceImpl(private val mapperDao: MapperDao, private val prefixDao: PrefixDao)(
  private val c: Resource[Task, (CollectionOperator[Mapper], CollectionOperator[Prefix])])(
                               private val cacheTtlInMillis: Int, private val cacheByGroupSize: Long = 100L) extends RoutesService {

  private val cacheByGroup: CustomCache[String, RoutesResourcePayload] = new CustomCacheImpl[String, RoutesResourcePayload](
    groupName => findByGroupInternal(groupName)
  )(maximumSize = cacheByGroupSize, ttl = cacheTtlInMillis.millisecond)

  override def findAll(): Task[RoutesResourcePayload] = c.use {
    case (mapperOperator, prefixesOperator) =>
      Task.parMap2(
        findAllMappers(mapperOperator),
        findAllPrefixes(prefixesOperator)
      )((mappers, prefixes) => RoutesResourcePayload(mappers = Some(mappers), prefixes = Some(prefixes)))
  }

  override def findAllMappers(): Task[Map[String, MapperRecordPayload]] = c.use {
    case (mapperOperator, _) => findAllMappers(mapperOperator)
  }

  override def findAllPrefixes(): Task[Map[String, String]] = c.use {
    case (_, prefixesOperator) => findAllPrefixes(prefixesOperator)
  }

  private def findAllPrefixes(prefixesOperator: CollectionOperator[Prefix]): Task[Map[String, String]] =
    prefixDao.findAll(prefixesOperator)
      .map(prefix => (prefix.groupName, prefix.value))
      .foldLeftL(Map.empty[String, String])((prevState, p) => prevState + p)

  private def findAllMappers(mapperOperator: CollectionOperator[Mapper]): Task[Map[String, MapperRecordPayload]] =
    mapperDao.findAll(mapperOperator)
      .map(mapper => (mapper.route, mapper.httpMethod, MappingContent.toMappingPayload(mapper.mappingContent)))
      .foldLeftL(Map.empty[String, MapperRecordPayload])((prevState, p) => prevState + (p._1 -> MapperRecordPayload(p._3, Some(p._2))))

  override def findByGroup(groupName: String): Task[RoutesResourcePayload] =
    cacheByGroup.get(groupName)

  private def findByGroupInternal(groupName: String): Task[RoutesResourcePayload] = c.use {
    case (mapperOperator, prefixesOperator) =>
      Task.parMap2(
        findAllMappers(mapperOperator).map(mappers => searchMappers(mappers, groupName)),
        findAllPrefixes(prefixesOperator)
          .map(_.get(groupName).map(prefix => Map.from(List((groupName, prefix)))).getOrElse(Map.empty))
      )((mappers, prefixes) => RoutesResourcePayload(mappers = Some(mappers), prefixes = Some(prefixes)))
  }

  private def searchMappers(data: Map[String, MapperRecordPayload], groupName: String): Map[String, MapperRecordPayload] = {
    def escape(route: String): String = route.split("/")
      .map(_.trim)
      .filter(_.nonEmpty)
      .map {
        case s"{{$_}}" => "{{}}"
        case v => v
      }.mkString("/")

    val direct = data.filter(entry => entry._2.mapping.value match {
      case Left(gn) if gn == groupName => true
      case _ => false
    })
    val escapedDirectRoutes = direct.keySet.map(escape)

    def rcSearch(mappingPayload: MappingPayload): Boolean =
      mappingPayload.value match {
        case Left(gn) if escapedDirectRoutes.contains(escape(gn)) => true // regex would fit here better (probably)
        case Left(_) => false
        case Right(mappingPayload) => mappingPayload.map(entry => rcSearch(entry._2)).exists(_ == true)
      }

    val indirect = data.filter(entry => rcSearch(entry._2.mapping))
    direct.concat(indirect)
    // todo mszmal: come back here, once routing logic is ready (right now it might not work in the way it should at the end)
  }

  override def save(routesResourcePayload: RoutesResourcePayload): Task[(List[Either[PersistenceError, String]], List[Either[PersistenceError, String]])] =
    c.use {
      case (mapperOperator, prefixesOperator) =>
        Task.parZip2(
          Task.parTraverse(routesResourcePayload.mappers.getOrElse(Map.empty).toList) { entry =>
            mapperDao.saveMapper(entry._1, entry._2)(mapperOperator)
          },
          Task.parTraverse(routesResourcePayload.prefixes.getOrElse(Map.empty).toList) { entry =>
            prefixDao.savePrefix(entry._1, entry._2)(prefixesOperator)
          }
        )
    }

  override def saveWithOverrides(routesResourcePayload: RoutesResourcePayload): Task[(List[Either[PersistenceError, String]], List[Either[PersistenceError, String]])] =
    c.use {
      case (mapperOperator, prefixesOperator) =>
        Task.parZip2(
          Task.parTraverse(routesResourcePayload.mappers.getOrElse(Map.empty).toList) { entry =>
            mapperDao.saveOrUpdateMapper(entry._1, entry._2)(mapperOperator)
          },
          Task.parTraverse(routesResourcePayload.prefixes.getOrElse(Map.empty).toList) { entry =>
            prefixDao.saveOrUpdatePrefix(entry._1, entry._2)(prefixesOperator)
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
            prefixDao.delete(item)(prefixesOperator)
          }
        )
    }

}