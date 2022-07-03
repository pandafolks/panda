package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.dto.MapperRecordDto
import com.github.pandafolks.panda.routes.mappers.Mapper
import com.github.pandafolks.panda.utils.PersistenceError
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task

trait RoutesDao {

  /**
   * Saves in the persistence layer the [[Mapper]] with the requested route and [[MapperRecordDto]]
   * if the one for specified route does not exist.
   *
   * @param route
   * @param mapperRecordDto
   * @param mapperOperator    [[Mapper]] DB entry point
   *
   * @return                  Either route if saved successfully or PersistenceError if the error during saving occurred
   */
  def saveRoute(route: String, mapperRecordDto: MapperRecordDto)(mapperOperator: CollectionOperator[Mapper]): Task[Either[PersistenceError, String]]
}
