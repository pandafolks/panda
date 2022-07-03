package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.dto.MapperRecordDto
import com.github.pandafolks.panda.routes.mappers.Mapper
import com.github.pandafolks.panda.utils.PersistenceError
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task
import monix.reactive.Observable

trait MapperDao {

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
  def saveMapper(route: String, mapperRecordDto: MapperRecordDto)(mapperOperator: CollectionOperator[Mapper]): Task[Either[PersistenceError, String]]

  /**
   * Returns all [[Mapper]] present in the persistence layer.
   *
   * @param prefixOperator    [[Mapper]] DB entry point
   *
   * @return                  Observable of [[Mapper]]
   */
  def findAll(prefixOperator: CollectionOperator[Mapper]): Observable[Mapper]
}
