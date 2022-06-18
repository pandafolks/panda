package com.github.pandafolks.panda.db

final case class DbConfig(
                           contactPoints: List[ContactPoint],
                           mode: String,
                           username: String,
                           password: String,
                           dbName: String,
                         )

final case class ContactPoint(host: String, port: Int)
