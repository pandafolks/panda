package com.github.pandafolks.panda.nodestracker

import monix.connect.mongodb.client.CollectionCodecRef
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.bson.types.ObjectId
import org.mongodb.scala.bson.codecs.Macros.createCodecProvider

final case class Job(name: String, nodeId: ObjectId)

object Job {
  final val JOBS_COLLECTION_NAME = "jobs"

  final val NAME_PROPERTY_NAME = "name"
  final val NODE_ID_PROPERTY_NAME = "nodeId"

  def getCollection(dbName: String, collectionName: String = JOBS_COLLECTION_NAME): CollectionCodecRef[Job] =
    CollectionCodecRef(
      dbName,
      collectionName,
      classOf[Job],
      fromRegistries(fromProviders(classOf[Job]))
    )
}
