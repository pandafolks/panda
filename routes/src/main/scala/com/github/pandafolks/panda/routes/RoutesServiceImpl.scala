package com.github.pandafolks.panda.routes

import cats.effect.Resource
import com.github.pandafolks.panda.routes.entity.{Mapper, MappingContent, Prefix}
import com.github.pandafolks.panda.routes.filter.StandaloneFilter
import com.github.pandafolks.panda.routes.payload.{MapperRecordPayload, MappingPayload, RoutesRemovePayload, RoutesResourcePayload}
import com.github.pandafolks.panda.utils.PersistenceError
import com.github.pandafolks.panda.utils.cache.{CustomCache, CustomCacheImpl}
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task

import scala.annotation.nowarn
import scala.concurrent.duration.DurationInt

final class RoutesServiceImpl(
    private val mapperDao: MapperDao,
    private val prefixDao: PrefixDao
)(private val c: Resource[Task, (CollectionOperator[Mapper], CollectionOperator[Prefix])])(
    private val cacheTtlInMillis: Int,
    private val cacheByGroupSize: Long = 100L
) extends RoutesService {

  private val cacheByGroup: CustomCache[String, RoutesResourcePayload] =
    new CustomCacheImpl[String, RoutesResourcePayload](groupName => findByGroupInternal(groupName))(
      maximumSize = cacheByGroupSize,
      ttl = cacheTtlInMillis.millisecond
    )

  override def findAll(standaloneFilter: StandaloneFilter = StandaloneFilter.All): Task[RoutesResourcePayload] = c.use {
    case (mapperOperator, prefixesOperator) =>
      Task.parMap2(
        findAllMappersInternal(mapperOperator, standaloneFilter),
        findAllPrefixesInternal(prefixesOperator)
      )((mappers, prefixes) => RoutesResourcePayload(mappers = Some(mappers), prefixes = Some(prefixes)))
  }

  override def findAllMappers(
      standaloneFilter: StandaloneFilter = StandaloneFilter.All
  ): Task[List[(String, MapperRecordPayload)]] = c.use { case (mapperOperator, _) =>
    findAllMappersInternal(mapperOperator, standaloneFilter)
  }

  override def findAllPrefixes(): Task[Map[String, String]] = c.use { case (_, prefixesOperator) =>
    findAllPrefixesInternal(prefixesOperator)
  }

  private def findAllPrefixesInternal(prefixesOperator: CollectionOperator[Prefix]): Task[Map[String, String]] =
    prefixDao
      .findAll(prefixesOperator)
      .foldLeftL(Map.empty[String, String])((prevState, prefix) => prevState + (prefix.groupName -> prefix.value))

  private def findAllMappersInternal(
      mapperOperator: CollectionOperator[Mapper],
      standaloneFilter: StandaloneFilter = StandaloneFilter.All
  ): Task[List[(String, MapperRecordPayload)]] =
    mapperDao
      .findAll(mapperOperator)
      .filter(standaloneFilter.filter)
      .foldLeftL(List.empty[(String, MapperRecordPayload)])((prevState, entry) =>
        (
          entry.route,
          MapperRecordPayload(
            mapping = MappingContent.toMappingPayload(entry.mappingContent),
            method = Some(entry.httpMethod.getName),
            isStandalone = Some(entry.isStandalone)
          )
        ) :: prevState
      )

  override def findByGroup(
      groupName: String,
      standaloneFilter: StandaloneFilter = StandaloneFilter.All
  ): Task[RoutesResourcePayload] =
    cacheByGroup
      .get(groupName)
      .map(payload =>
        RoutesResourcePayload(
          mappers = payload.mappers.map(_.filter(entry => standaloneFilter.filter(entry._2))),
          prefixes = payload.prefixes
        )
      )

  private def findByGroupInternal(groupName: String): Task[RoutesResourcePayload] = c.use { case (mapperOperator, prefixesOperator) =>
    Task.parMap2(
      findAllMappersInternal(mapperOperator).map(mappers => searchMappers2point0(mappers, groupName)),
      findAllPrefixesInternal(prefixesOperator)
        .map(_.get(groupName).map(prefix => Map.from(List((groupName, prefix)))).getOrElse(Map.empty))
    )((mappers, prefixes) => RoutesResourcePayload(mappers = Some(mappers), prefixes = Some(prefixes)))
  }

  @deprecated("Cannot handle composition routes with depth bigger than 1", since = "0.1.0")
  @nowarn
  private def searchMappers(
      data: List[(String, MapperRecordPayload)],
      groupName: String
  ): List[(String, MapperRecordPayload)] = {
    def escape(route: String): String =
      route
        .split("/")
        .map(_.trim)
        .filter(_.nonEmpty)
        .map {
          case s"{{$_}}" => "{{}}"
          case v         => v
        }
        .mkString("/")

    val direct = data.filter(entry =>
      entry._2.mapping.value match {
        case Left(gn) if gn == groupName => true
        case _                           => false
      }
    )
    val escapedDirectRoutes =
      direct
        .map(mapping => (escape(mapping._1), mapping._2.method))
        .toSet // cuz two routes with different methods can point to different groups

    def rcSearch(mappingPayload: MappingPayload, method: Option[String]): Boolean =
      mappingPayload.value match {
        case Left(gn) if escapedDirectRoutes.contains((escape(gn), method)) =>
          true // regex would fit here better (probably)
        case Left(_)               => false
        case Right(mappingPayload) => mappingPayload.map(entry => rcSearch(entry._2, method)).exists(_ == true)
      }

    val indirect = data.filter(entry => rcSearch(entry._2.mapping, entry._2.method))
    direct.concat(indirect)
  }

  private def searchMappers2point0(
      data: List[(String, MapperRecordPayload)],
      groupName: String
  ): List[(String, MapperRecordPayload)] = {
    def escape(route: String): String =
      route
        .split("/")
        .map(_.trim)
        .filter(_.nonEmpty)
        .map {
          case s"{{$_}}" => "{{}}"
          case v         => v
        }
        .mkString("/")

    def returnAllEscapedRoutesForMappingPayload(mappingPayload: MappingPayload): List[String] =
      mappingPayload.value match {
        case Left(route) => List(escape(route))
        case Right(map) =>
          map.foldLeft(List.empty[String])((prevState, el) => prevState ::: returnAllEscapedRoutesForMappingPayload(el._2))
      }

    val dataSetToSearchThrough: Map[Boolean, List[(String, MapperRecordPayload)]] = data
      .filter(entry =>
        entry._2.mapping.value match { // at this point we want to find both all direct and composition mappers
          case Left(gn) if gn == groupName => true
          case Left(_)                     => false
          case Right(_)                    => true
        }
      )
      .groupBy(_._2.mapping.value.isLeft) // true - direct with group match, false - all composition

    val direct: List[(String, MapperRecordPayload)] = dataSetToSearchThrough.getOrElse(true, List.empty)
    val compositionMappers: List[(String, MapperRecordPayload)] = dataSetToSearchThrough.getOrElse(false, List.empty)

    val mapByNestedRoutesWithRelatedMethod: Map[(String, Option[String]), Set[String]] = compositionMappers
      .map(entry => ((escape(entry._1), entry._2.method), returnAllEscapedRoutesForMappingPayload(entry._2.mapping)))
      .foldLeft(Map.empty[(String, Option[String]), Set[String]])((prevState, entry) =>
        entry._2.foldLeft(prevState)((prevSubState, escapedRouteForMappingPayload) =>
          prevSubState + ((escapedRouteForMappingPayload, entry._1._2) ->
            (prevSubState.getOrElse((escapedRouteForMappingPayload, entry._1._2), Set.empty) + entry._1._1))
        )
      )

    def whileFunc(
        needsToBeChecked: List[(String, Option[String])],
        groupedCompositionRoutes: Map[(String, Option[String]), (String, MapperRecordPayload)],
        indirect: List[(String, MapperRecordPayload)] = List.empty
    ): List[(String, MapperRecordPayload)] =
      if (needsToBeChecked.nonEmpty) {
        Function.tupled(whileFunc _) {
          // the first is nextLevel, the second indirect
          needsToBeChecked.foldLeft((List.empty[(String, Option[String])], groupedCompositionRoutes, indirect))(
            (prevState, needsToBeCheckedSingle) => {
              mapByNestedRoutesWithRelatedMethod.get(needsToBeCheckedSingle) match {
                case Some(setWithRoutes) =>
                  setWithRoutes.foldLeft(prevState)((prevStateNested, route) => {
                    val lookingFor = (route, needsToBeCheckedSingle._2)
                    val newIndirect = groupedCompositionRoutes
                      .get(lookingFor)
                      .map(_ :: prevStateNested._3)
                      .getOrElse(prevStateNested._3)
                    val nextLevel =
                      if (groupedCompositionRoutes.contains(lookingFor)) lookingFor :: prevStateNested._1
                      else prevStateNested._1
                    (
                      nextLevel,
                      groupedCompositionRoutes - lookingFor,
                      newIndirect
                    ) // groupedCompositionRoutes - lookingFor => crucial in order to be able to process circular dependencies
                  })
                case None => prevState
              }
            }
          )
        }
      } else indirect

    direct.concat(
      whileFunc(
        direct.map(entry => (escape(entry._1), entry._2.method)), // cuz two routes with different methods can point to different groups
        compositionMappers.foldLeft(Map.empty[(String, Option[String]), (String, MapperRecordPayload)])((prevState, entry) =>
          prevState + ((escape(entry._1), entry._2.method) -> entry)
        )
      )
    )
  }

  override def save(
      routesResourcePayload: RoutesResourcePayload
  ): Task[(List[Either[PersistenceError, String]], List[Either[PersistenceError, String]])] =
    c.use { case (mapperOperator, prefixesOperator) =>
      Task.parZip2(
        Task.parTraverse(routesResourcePayload.mappers.getOrElse(List.empty)) { entry =>
          mapperDao.saveMapper(entry._1, entry._2)(mapperOperator)
        },
        Task.parTraverse(routesResourcePayload.prefixes.getOrElse(Map.empty).toList) { entry =>
          prefixDao.savePrefix(entry._1, entry._2)(prefixesOperator)
        }
      )
    }

  override def saveWithOverrides(
      routesResourcePayload: RoutesResourcePayload
  ): Task[(List[Either[PersistenceError, String]], List[Either[PersistenceError, String]])] =
    c.use { case (mapperOperator, prefixesOperator) =>
      Task.parZip2(
        Task.parTraverse(routesResourcePayload.mappers.getOrElse(List.empty)) { entry =>
          mapperDao.saveOrUpdateMapper(entry._1, entry._2)(mapperOperator)
        },
        Task.parTraverse(routesResourcePayload.prefixes.getOrElse(Map.empty).toList) { entry =>
          prefixDao.saveOrUpdatePrefix(entry._1, entry._2)(prefixesOperator)
        }
      )
    }

  override def delete(
      routesRemovePayload: RoutesRemovePayload
  ): Task[(List[Either[PersistenceError, String]], List[Either[PersistenceError, String]])] =
    c.use { case (mapperOperator, prefixesOperator) =>
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
