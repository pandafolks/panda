package com.github.mattszm.panda

import shapeless.tag
import shapeless.tag.@@

import java.util.UUID

package object user {
  type UserId = UUID @@ UserIdTag

  def tagUUIDAsUserId(id: UUID): UserId = tag[UserIdTag][UUID](id)
}
