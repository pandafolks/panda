package com.github.mattszm.panda.user.token

import cats.data.OptionT
import cats.effect.Resource
import com.github.mattszm.panda.user.{User, UserId}
import com.google.common.cache.{Cache, CacheBuilder}
import com.mongodb.client.model.Filters
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task
import org.reactormonk.{CryptoBits, PrivateKey}
import scalacache.guava.GuavaCache
import scalacache.memoization.memoizeF
import scalacache.{CacheConfig, Mode, _}

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.io.{Codec, Source}
import scala.util.{Random, Try}


final class TokenServiceImpl(private val config: TokensConfig, val c: Resource[Task, CollectionOperator[Token]]) extends TokenService {
  //todo: add background job which clears old, unused tokens

  private val readmeText : String = Try { Source.fromResource("tokenKey.txt").getLines().mkString }
    .getOrElse(Random.alphanumeric.take(20).mkString(""))
  private final val key = PrivateKey(Codec.toUTF8(readmeText))
  private final val crypto = CryptoBits(key)
  private final val clock = java.time.Clock.systemUTC
  private final val tokenTimeToLive = config.timeToLive

  private val underlyingGuavaCache: Cache[String, Entry[Option[Token]]] = CacheBuilder.newBuilder()
    .maximumSize(10000L)
    .build[String, Entry[Option[Token]]]
  implicit private val cache: GuavaCache[Option[Token]] = GuavaCache(underlyingGuavaCache)(CacheConfig.defaultCacheConfig)
  implicit private val mode: Mode[Task] = scalacache.CatsEffect.modes.async

  private def getToken(tempId: String): Task[Option[Token]] =
    memoizeF[Task, Option[Token]](Some(60.seconds)) { // purpose: better handling of the batched requests (maintenance scripts, etc)
      c.use {
        case tokenOperator => tokenOperator.source
          .find(Filters.eq("tempId", tempId))
          .toListL
          .map(_.sortBy(t => -t.creationTimeStamp)) // very little collision chance
          .map(_.headOption)
      }
    }

  def signToken(user: User): Task[String] =
    c.use {
      case tokenOperator =>
        for {
          tempId <- Task.now(UUID.randomUUID().toString)
          creationTimeStamp = clock.instant().toEpochMilli
          _ <- tokenOperator.single.insertOne(Token(tempId, user._id, creationTimeStamp))
          signedToken <- Task.eval(crypto.signToken(tempId, creationTimeStamp.toString))
        } yield signedToken
    }

  def validateSignedToken(token: String): Task[Option[UserId]] =
    for {
      tokenEntity <- OptionT(Task.eval(crypto.validateSignedToken(token)))
        .flatMap(validatedSignedToken => OptionT(getToken(validatedSignedToken))).value
      tokenEntityWithExpiryCheck <- Task.eval(tokenEntity.filter(t =>
        Instant.ofEpochMilli(t.creationTimeStamp).plusSeconds(tokenTimeToLive).isAfter(clock.instant())))
    } yield tokenEntityWithExpiryCheck.map(_.userId)

}
