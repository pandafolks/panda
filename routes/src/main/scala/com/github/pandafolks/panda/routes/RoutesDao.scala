package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.dto.MapperRecordDto
import com.github.pandafolks.panda.routes.mappers.Mapper
import com.github.pandafolks.panda.utils.PersistenceError
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task

trait RoutesDao {

  // todo mszmal: add docs
  def saveRoute(route: String, mapperRecordDto: MapperRecordDto)(mapperOperator: CollectionOperator[Mapper]): Task[Either[PersistenceError, String]]
}
