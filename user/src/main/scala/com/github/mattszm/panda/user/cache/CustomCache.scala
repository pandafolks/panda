package com.github.mattszm.panda.user.cache

import monix.eval.Task

trait CustomCache[K, V] {
  def get(key: K): Task[V]
}
