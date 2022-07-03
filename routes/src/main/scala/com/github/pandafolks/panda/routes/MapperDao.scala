package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.entity.Mapper
import com.github.pandafolks.panda.routes.payload.MapperRecordPayload
import com.github.pandafolks.panda.utils.PersistenceError
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task
import monix.reactive.Observable

trait MapperDao {

  /**
   * Saves in the persistence layer the [[Mapper]] with the requested route and [[MapperRecordPayload]]
   * if the one for specified route does not exist.
   *
   * @param route
   * @param mapperRecordDto
   * @param mapperOperator    [[Mapper]] DB entry point
   * @return                  Either route if saved successfully or PersistenceError if the error during saving occurred
   */
  def saveMapper(route: String, mapperRecordDto: MapperRecordPayload)(mapperOperator: CollectionOperator[Mapper]): Task[Either[PersistenceError, String]]

  /**
   * Returns all [[Mapper]] present in the persistence layer.
   *
   * @param mapperOperator    [[Mapper]] DB entry point
   * @return                  Observable of [[Mapper]]
   */
  def findAll(mapperOperator: CollectionOperator[Mapper]): Observable[Mapper]

  /**
   * Removes [[Mapper]] associated with requested route and HTTP method.
   *
   * @param route
   * @param method
   * @param mapperOperator    [[Mapper]] DB entry point
   * @return                  Either route if deleted successfully or PersistenceError if the error during deletion occurred
   */
  def delete(route: String, method: Option[String])(mapperOperator: CollectionOperator[Mapper]): Task[Either[PersistenceError, String]]
}
