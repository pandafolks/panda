package com.github.pandafolks.panda.routes

import cats.effect.Resource
import cats.effect.concurrent.Ref
import com.github.pandafolks.panda.routes.entity.{Mapper, Prefix}
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task

final class TreesServiceImpl private(
                                      private val mapperDao: MapperDao,
                                      private val prefixDao: PrefixDao,
                                    )(
                                      private val routesTreesHandler: Ref[Task, RoutesTreesHandler]
                                    )(
                                      private val c: Resource[Task, (CollectionOperator[Mapper], CollectionOperator[Prefix])]
                                    ) extends TreesService {


  private def loadTrees(): Task[Unit] = {
    for {
      groupedMappers <- c.use {
        case (mapperOperator, _) => mapperDao.findAll(mapperOperator).toListL.map(_.groupBy(_.httpMethod))
      }
      _ <- routesTreesHandler.set(RoutesTreesHandler.construct(groupedMappers))
    } yield ()
  }

  loadTrees() // todo mszmal: start here
}


object TreesServiceImpl {
  def apply(mapperDao: MapperDao, prefixDao: PrefixDao)(
    c: Resource[Task, (CollectionOperator[Mapper], CollectionOperator[Prefix])]): Task[TreesService] =
    for {
      groupedMappers <- c.use {
        case (mapperOperator, _) => mapperDao.findAll(mapperOperator).toListL.map(_.groupBy(_.httpMethod))
      }
      routesTreesHandler <- Ref.of[Task, RoutesTreesHandler](RoutesTreesHandler.construct(groupedMappers))
    } yield new TreesServiceImpl(mapperDao, prefixDao)(routesTreesHandler)(c)
}
