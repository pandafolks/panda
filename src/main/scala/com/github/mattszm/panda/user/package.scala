package com.github.mattszm.panda

import io.chrisdavenport.fuuid.FUUID
import shapeless.tag
import shapeless.tag.@@

package object user {
  type UserId = FUUID @@ UserIdTag

  def tagFUUIDAsUserId(id: FUUID): UserId = tag[UserIdTag][FUUID](id)
}
