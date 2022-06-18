package com.github.pandafolks.panda.user.cache

import com.google.common.cache.{Cache, CacheBuilder}
import monix.eval.Task
import scalacache.guava.GuavaCache
import scalacache.memoization.memoizeF
import scalacache.{CacheConfig, Entry, Mode}

import scala.concurrent.duration.Duration

final class CustomCacheImpl[K, V](loader: K => Task[V])(
  private val maximumSize: Long, private val ttl: Duration) extends CustomCache[K, V] {

  private val underlyingGuavaCache: Cache[String, Entry[V]] = CacheBuilder.newBuilder()
    .maximumSize(maximumSize)
    .build[String, Entry[V]]
  implicit private val cache: GuavaCache[V] = GuavaCache(underlyingGuavaCache)(CacheConfig.defaultCacheConfig)
  implicit private val mode: Mode[Task] = scalacache.CatsEffect.modes.async

  def get(key: K): Task[V] = memoizeF[Task, V](Some(ttl)) { loader(key) }
}
