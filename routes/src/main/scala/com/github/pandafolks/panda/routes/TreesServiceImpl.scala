package com.github.pandafolks.panda.routes

import cats.effect.Resource
import cats.effect.concurrent.Ref
import com.github.pandafolks.panda.backgroundjobsregistry.BackgroundJobsRegistry
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
                                      private val backgroundJobsRegistry: BackgroundJobsRegistry,
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
    if (treesHandlerRefreshIntervalInMillis > 0) {
      // Initial cache load is made by TreesServiceImpl#apply
      backgroundJobsRegistry.addJobAtFixedRate(treesHandlerRefreshIntervalInMillis.millisecond, treesHandlerRefreshIntervalInMillis.millisecond)(
        () => reloadTreesIfNecessary()
          .onErrorRecover { e: Throwable => logger.error(s"Cannot reload ${RoutesTreesHandler.getClass.getName}", e) },
        "TreesServiceReloadTrees"
      )
    }
  }

  override def findRoute(path: Uri.Path, method: Method): Task[Option[(RouteInfo, Map[String, String])]] =
    routesTreesHandler.get.map(_.getTree(method).flatMap(_.find(path)))

  override def findStandaloneRoute(path: Uri.Path, method: Method): Task[Option[(RouteInfo, Map[String, String])]] =
    routesTreesHandler.get.map(_.getTree(method).flatMap(_.find(path, standaloneOnly = true)))

  override def findPrefix(group: Group): Task[Uri.Path] =
    routesTreesHandler.get.map(handler => Path.unsafeFromString(handler.getPrefix(group.name).getOrElse("")))

  @VisibleForTesting
  private def reloadTreesIfNecessary(): Task[Unit] =
    Task.eval(logger.debug("Starting TreesServiceImpl#reloadTreesIfNecessary job")) >>
    c.use {
      case (mapperOperator, prefixOperator) => for {
        needsUpdate <- Task.parZip2(
          latestSeenMappingTimestamp.get.flatMap(t => mapperDao.checkIfThereAreNewerMappings(t)(mapperOperator)),
          latestSeenPrefixTimestamp.get.flatMap(t => prefixDao.checkIfThereAreNewerPrefixes(t)(prefixOperator))
        )
        data <- Task.parZip2(
          if (needsUpdate._1) {
            logger.info("Detected changes inside Mappers - refreshing the Mappers' caches")
            mapperDao.findAll(mapperOperator).toListL.map(_.groupBy(_.httpMethod)).map(Some(_))
          } else Task.now(Option.empty),
          if (needsUpdate._2) {
            logger.info("Detected changes inside Prefixes - refreshing the Prefixes' caches")
            prefixDao.findAll(prefixOperator)
              .foldLeft(Map.empty[String, Prefix])((prevState, prefix) => prevState + (prefix.groupName -> prefix))
              .firstOptionL
          } else Task.now(Option.empty)
        )
        _ <- data match {
          case (Some(mappers), Some(prefixes)) =>
            routesTreesHandler.set(RoutesTreesHandler.construct(mappers, prefixes)) >>
              latestSeenMappingTimestamp.set(TreesServiceImpl.findLatestSeenMappingTimestamp(mappers)) >>
              latestSeenPrefixTimestamp.set(TreesServiceImpl.findLatestSeenPrefixTimestamp(prefixes))
          case (Some(mappers), None) =>
            routesTreesHandler.update(previousState => RoutesTreesHandler.withNewMappers(previousState, mappers)) >>
              latestSeenMappingTimestamp.set(TreesServiceImpl.findLatestSeenMappingTimestamp(mappers))
          case (None, Some(prefixes)) =>
            routesTreesHandler.update(previousState => RoutesTreesHandler.withNewPrefixes(previousState, prefixes)) >>
              latestSeenPrefixTimestamp.set(TreesServiceImpl.findLatestSeenPrefixTimestamp(prefixes))
          case (None, None) => Task.unit
        }
      } yield ()
    }
}

object TreesServiceImpl {
  def apply(
             mapperDao: MapperDao,
             prefixDao: PrefixDao,
             backgroundJobsRegistry: BackgroundJobsRegistry,
           )(
             c: Resource[Task, (CollectionOperator[Mapper], CollectionOperator[Prefix])]
           )(
             treesHandlerRefreshIntervalInMillis: Int
           ): Task[TreesService] =
    c.use {
      case (mapperOperator, prefixOperator) => for {
        data <- Task.parZip2(
          mapperDao.findAll(mapperOperator).toListL.map(_.groupBy(_.httpMethod)),
          prefixDao.findAll(prefixOperator)
            .foldLeft(Map.empty[String, Prefix])((prevState, prefix) => prevState + (prefix.groupName -> prefix))
            .firstL
        )
        routesTreesHandler <- Ref.of[Task, RoutesTreesHandler](RoutesTreesHandler.construct(data._1, data._2))
        latestSeenMappingTimestamp <- Ref.of[Task, Long](findLatestSeenMappingTimestamp(data._1))
        latestSeenPrefixTimestamp <- Ref.of[Task, Long](findLatestSeenPrefixTimestamp(data._2))
      } yield new TreesServiceImpl(mapperDao, prefixDao, backgroundJobsRegistry, treesHandlerRefreshIntervalInMillis)(
        routesTreesHandler, latestSeenMappingTimestamp, latestSeenPrefixTimestamp)(c)
    }

  def findLatestSeenMappingTimestamp(mappers: Map[HttpMethod, List[Mapper]]): Long =
    mappers.values.flatten.maxByOption(_.lastUpdateTimestamp).map(_.lastUpdateTimestamp).getOrElse(0L)

  def findLatestSeenPrefixTimestamp(prefixes: Map[String, Prefix]): Long =
    prefixes.values.maxByOption(_.lastUpdateTimestamp).map(_.lastUpdateTimestamp).getOrElse(0L)
}
