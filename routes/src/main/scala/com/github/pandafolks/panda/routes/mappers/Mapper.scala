package com.github.pandafolks.panda.routes.mappers

import monix.connect.mongodb.client.CollectionCodecRef
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.codecs.Macros.createCodecProvider

final case class Mapper(route: String, mappingContent: MappingContent, httpMethod: String, lastUpdateTimestamp: Long)

object Mapper {
  final val MAPPERS_COLLECTION_NAME = "mappers"

  def getCollection(dbName: String, collectionName: String = MAPPERS_COLLECTION_NAME): CollectionCodecRef[Mapper] =
    CollectionCodecRef(
      dbName,
      collectionName,
      classOf[Mapper],
      fromRegistries(fromProviders(
        classOf[Mapper],
        classOf[MappingContent]
      ), DEFAULT_CODEC_REGISTRY)
    )
}
