package com.github.pandafolks.panda.nodestracker

import monix.connect.mongodb.client.CollectionCodecRef
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.bson.types.ObjectId
import org.mongodb.scala.bson.codecs.Macros.createCodecProvider

final case class Node(_id: ObjectId, lastUpdateTimestamp: Long)

object Node {
  final val NODES_COLLECTION_NAME = "nodes"

  def getCollection(dbName: String, collectionName: String = NODES_COLLECTION_NAME): CollectionCodecRef[Node] = CollectionCodecRef(
    dbName,
    collectionName,
    classOf[Node],
    fromRegistries(fromProviders(classOf[Node]))
  )
}