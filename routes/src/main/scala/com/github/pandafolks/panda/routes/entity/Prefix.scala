package com.github.pandafolks.panda.routes.entity

import monix.connect.mongodb.client.CollectionCodecRef
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.bson.codecs.Macros.createCodecProvider

final case class Prefix(groupName: String, value: String, lastUpdateTimestamp: Long)

object Prefix {
  final val PREFIXES_COLLECTION_NAME = "prefixes"

  final val GROUP_NAME_PROPERTY_NAME = "groupName"
  final val VALUE_PROPERTY_NAME = "value"
  final val LAST_UPDATE_TIMESTAMP_PROPERTY_NAME = "lastUpdateTimestamp"

  def getCollection(dbName: String, collectionName: String = PREFIXES_COLLECTION_NAME): CollectionCodecRef[Prefix] =
    CollectionCodecRef(
      dbName,
      collectionName,
      classOf[Prefix],
      fromRegistries(fromProviders(classOf[Prefix]))
    )
}