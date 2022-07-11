package com.github.pandafolks.panda.user

import monix.connect.mongodb.client.CollectionCodecRef
import org.bson.UuidRepresentation
import org.bson.codecs.UuidCodec
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.mongodb.scala.bson.codecs.Macros.createCodecProvider
import tsec.passwordhashers.PasswordHash
import tsec.passwordhashers.jca.BCrypt

final case class User(id: UserId, username: String, password: PasswordHash[BCrypt])

object User {
  final val USERS_COLLECTION_NAME = "users"

  private val javaCodecs: CodecRegistry = CodecRegistries.fromCodecs(
    new UuidCodec(UuidRepresentation.STANDARD)
  )

  def getCollection(dbName: String, collectionName: String = USERS_COLLECTION_NAME): CollectionCodecRef[User] =
    CollectionCodecRef(
      dbName,
      collectionName,
      classOf[User],
      fromRegistries(fromProviders(classOf[User]), javaCodecs)
    )
}

