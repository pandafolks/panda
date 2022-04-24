package com.github.mattszm.panda.user

import tsec.passwordhashers.PasswordHash
import tsec.passwordhashers.jca.BCrypt

final case class User(id: UserId, username: String, password: PasswordHash[BCrypt])
