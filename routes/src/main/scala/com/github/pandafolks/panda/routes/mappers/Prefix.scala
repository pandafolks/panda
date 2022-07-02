package com.github.pandafolks.panda.routes.mappers

import monix.connect.mongodb.client.CollectionCodecRef
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.bson.codecs.Macros.createCodecProvider

final case class Prefix(groupName: String, value: String)

object Prefix {
  final val PREFIXES_COLLECTION_NAME = "prefixes"

  def getCollection(dbName: String, collectionName: String = PREFIXES_COLLECTION_NAME): CollectionCodecRef[Prefix] =
    CollectionCodecRef(
      dbName,
      collectionName,
      classOf[Prefix],
      fromRegistries(fromProviders(classOf[Prefix]))
    )
}