package com.github.pandafolks.panda.routes.mappers

import com.github.pandafolks.panda.routes.HttpMethod
import monix.connect.mongodb.client.CollectionCodecRef
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.bson.codecs.Macros.createCodecProvider

final case class Mapper(route: String, groupName: String, httpMethod: HttpMethod, lastUpdateTimestamp: Long)

object Mapper {
  final val MAPPERS_COLLECTION_NAME = "mappers"

  def getCollection(dbName: String, collectionName: String = MAPPERS_COLLECTION_NAME): CollectionCodecRef[Mapper] =
    CollectionCodecRef(
      dbName,
      collectionName,
      classOf[Mapper],
      fromRegistries(fromProviders(classOf[Mapper]))
    )
}
