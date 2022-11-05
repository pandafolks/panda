package com.github.pandafolks.panda.routes.entity

import com.github.pandafolks.panda.routes.HttpMethod
import monix.connect.mongodb.client.CollectionCodecRef
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.codecs.Macros.createCodecProvider

final case class Mapper(
    route: String,
    mappingContent: MappingContent,
    httpMethod: HttpMethod,
    isStandalone: Boolean,
    lastUpdateTimestamp: Long
)

object Mapper {
  final val MAPPERS_COLLECTION_NAME = "mappers"

  final val ROUTE_PROPERTY_NAME = "route"
  final val MAPPING_CONTENT_PROPERTY_NAME = "mappingContent"
  final val HTTP_METHOD_PROPERTY_NAME = "httpMethod"
  final val IS_STANDALONE_PROPERTY_NAME = "isStandalone"
  final val LAST_UPDATE_TIMESTAMP_PROPERTY_NAME = "lastUpdateTimestamp"

  def getCollection(dbName: String, collectionName: String = MAPPERS_COLLECTION_NAME): CollectionCodecRef[Mapper] =
    CollectionCodecRef(
      dbName,
      collectionName,
      classOf[Mapper],
      fromRegistries(
        fromProviders(
          classOf[Mapper],
          classOf[MappingContent],
          classOf[HttpMethod]
        ),
        DEFAULT_CODEC_REGISTRY
      )
    )
}
