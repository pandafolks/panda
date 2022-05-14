package com.github.mattszm.panda.user.token

import com.github.mattszm.panda.user.UserId

final case class Token(tempId: String, userId: UserId, creationTimeStamp: Long)
