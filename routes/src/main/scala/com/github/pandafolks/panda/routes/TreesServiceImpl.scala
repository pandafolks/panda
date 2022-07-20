package com.github.pandafolks.panda.routes

import cats.effect.concurrent.Ref
import monix.eval.Task

final class TreesServiceImpl private(
                                      private val mapperDao: MapperDao,
                                      private val prefixDao: PrefixDao,
                                   )(
                                     private val routesTreesHandler: Ref[Task, RoutesTreesHandler]
                                   ) extends TreesService {

  // todo mszmal: start here
}


object TreesServiceImpl {
  def apply(mapperDao: MapperDao, prefixDao: PrefixDao): Task[TreesService] =
    for {
      routesTreesHandler <- Ref.of[Task, RoutesTreesHandler](RoutesTreesHandler())
    } yield new TreesServiceImpl(mapperDao, prefixDao)(routesTreesHandler)
}
