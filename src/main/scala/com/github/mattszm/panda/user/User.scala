package com.github.mattszm.panda.user

import tsec.passwordhashers.PasswordHash
import tsec.passwordhashers.jca.BCrypt

final case class User(_id: UserId, username: String, password: PasswordHash[BCrypt])

