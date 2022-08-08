package com.github.pandafolks.panda.db

final case class DbConfig(
                           contactPoints: Option[List[ContactPoint]],
                           connectionString: Option[String],
                           mode: Option[String],
                           username: Option[String],
                           password: Option[String],
                           dbName: String,
                         )

final case class ContactPoint(host: String, port: Int)
