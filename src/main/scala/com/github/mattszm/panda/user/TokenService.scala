package com.github.mattszm.panda.user

import org.reactormonk.{CryptoBits, PrivateKey}

import scala.io.Codec
import scala.util.Random

object TokenService {
  private final val key = PrivateKey(Codec.toUTF8(Random.alphanumeric.take(20).mkString(""))) // in future it should be static across all instances
  private final val crypto = CryptoBits(key)
  private final val clock = java.time.Clock.systemUTC

  def signToken(user: User): String = crypto.signToken(user._id.toString, clock.millis.toString)

  def validateSignedToken(token: String): Option[String] = crypto.validateSignedToken(token)
}
