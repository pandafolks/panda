package com.github.pandafolks.panda.routes

import cats.effect.Resource
import cats.effect.concurrent.Ref
import com.github.pandafolks.panda.routes.RoutesTree.RouteInfo
import com.github.pandafolks.panda.routes.entity.{Mapper, Prefix}
import com.google.common.annotations.VisibleForTesting
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task
import org.http4s.Uri.Path
import org.http4s.{Method, Uri}
import org.slf4j.LoggerFactory

import scala.concurrent.duration.DurationInt

final class TreesServiceImpl private(
                                      private val mapperDao: MapperDao,
                                      private val prefixDao: PrefixDao,
                                      private val treesHandlerRefreshIntervalInMillis: Int
                                    )(
                                      private val routesTreesHandler: Ref[Task, RoutesTreesHandler],
                                      @VisibleForTesting private val latestSeenMappingTimestamp: Ref[Task, Long],
                                      @VisibleForTesting private val latestSeenPrefixTimestamp: Ref[Task, Long]

                                    )(
                                      private val c: Resource[Task, (CollectionOperator[Mapper], CollectionOperator[Prefix])]
                                    ) extends TreesService {
  private val logger = LoggerFactory.getLogger(getClass.getName)

  locally {
    import monix.execution.Scheduler.{global => scheduler}

    if (treesHandlerRefreshIntervalInMillis > 0) {
      // Initial cache load is made by TreesServiceImpl#apply
      scheduler.scheduleAtFixedRate(treesHandlerRefreshIntervalInMillis.millisecond, treesHandlerRefreshIntervalInMillis.millisecond) {
        reloadTreesIfNecessary()
          .onErrorRecover { e: Throwable => logger.error(s"Cannot reload ${RoutesTreesHandler.getClass.getName}", e) }
          .runToFuture(scheduler)
        ()
      }
    }
  }

  override def findRoute(path: Uri.Path, method: Method): Task[Option[(RouteInfo, Map[String, String])]] =
    routesTreesHandler.get.map(_.getTree(method).flatMap(_.find(path)))

  override def findStandaloneRoute(path: Uri.Path, method: Method): Task[Option[(RouteInfo, Map[String, String])]] =
    routesTreesHandler.get.map(_.getTree(method).flatMap(_.find(path, standaloneOnly = true)))

  override def findPrefix(group: Group): Task[Uri.Path] =
    routesTreesHandler.get.map(handler => Path.unsafeFromString(handler.getPrefix(group.name).getOrElse("")))

  @VisibleForTesting
  private def reloadTreesIfNecessary(): Task[Unit] = c.use {
    case (mapperOperator, prefixOperator) => for {
      needsUpdate <- Task.parZip2(
        latestSeenMappingTimestamp.get.flatMap(t => mapperDao.checkIfThereAreNewerMappings(t)(mapperOperator)),
        latestSeenPrefixTimestamp.get.flatMap(t => prefixDao.checkIfThereAreNewerPrefixes(t)(prefixOperator))
      )
      data <- Task.parZip2(
        if (needsUpdate._1)
          mapperDao.findAll(mapperOperator).toListL.map(_.groupBy(_.httpMethod)).map(Some(_))
        else Task.now(Option.empty),
        if (needsUpdate._2)
          prefixDao.findAll(prefixOperator).toListL.map(
            _.foldLeft(Map.empty[String, Prefix])((prevState, prefix) => prevState + (prefix.groupName -> prefix))
          ).map(Some(_))
        else Task.now(Option.empty)
      )
      _ <- data match {
        case (Some(mappers), Some(prefixes)) =>
          routesTreesHandler.set(RoutesTreesHandler.construct(mappers, prefixes)) >>
            latestSeenMappingTimestamp.set(findLatestSeenMappingTimestamp(mappers)) >>
            latestSeenPrefixTimestamp.set(findLatestSeenPrefixTimestamp(prefixes))
        case (Some(mappers), None) =>
          routesTreesHandler.update(previousState => RoutesTreesHandler.withNewMappers(previousState, mappers)) >>
            latestSeenMappingTimestamp.set(findLatestSeenMappingTimestamp(mappers))
        case (None, Some(prefixes)) =>
          routesTreesHandler.update(previousState => RoutesTreesHandler.withNewPrefixes(previousState, prefixes)) >>
            latestSeenPrefixTimestamp.set(findLatestSeenPrefixTimestamp(prefixes))
        case (None, None) => Task.unit
      }
    } yield ()
  }

  private def findLatestSeenMappingTimestamp(mappers: Map[HttpMethod, List[Mapper]]): Long =
    mappers.values.flatten.maxByOption(_.lastUpdateTimestamp).map(_.lastUpdateTimestamp).getOrElse(0L)

  private def findLatestSeenPrefixTimestamp(prefixes: Map[String, Prefix]): Long =
    prefixes.values.maxByOption(_.lastUpdateTimestamp).map(_.lastUpdateTimestamp).getOrElse(0L)
}

object TreesServiceImpl {
  def apply(mapperDao: MapperDao, prefixDao: PrefixDao
           )(c: Resource[Task, (CollectionOperator[Mapper], CollectionOperator[Prefix])]
           )(treesHandlerRefreshIntervalInMillis: Int): Task[TreesService] =
    c.use {
      case (mapperOperator, prefixOperator) => for {
        data <- Task.parZip2(
          mapperDao.findAll(mapperOperator).toListL.map(_.groupBy(_.httpMethod)),
          prefixDao.findAll(prefixOperator).toListL.map(
            _.foldLeft(Map.empty[String, Prefix])((prevState, prefix) => prevState + (prefix.groupName -> prefix))
          )
        )
        routesTreesHandler <- Ref.of[Task, RoutesTreesHandler](RoutesTreesHandler.construct(data._1, data._2))
        latestSeenMappingTimestamp <- Ref.of[Task, Long](data._1.values.flatten.maxByOption(_.lastUpdateTimestamp).map(_.lastUpdateTimestamp).getOrElse(0L))
        latestSeenPrefixTimestamp <- Ref.of[Task, Long](data._2.values.maxByOption(_.lastUpdateTimestamp).map(_.lastUpdateTimestamp).getOrElse(0L))
      } yield new TreesServiceImpl(mapperDao, prefixDao, treesHandlerRefreshIntervalInMillis)(
        routesTreesHandler, latestSeenMappingTimestamp, latestSeenPrefixTimestamp)(c)
    }
}
