package com.github.mattszm.panda.bootstrap.configuration.sub


final case class DbConfig(
                         contactPoints: List[ContactPoint],
                         username: String,
                         password: String,
                         dbName: String,
                         )

final case class ContactPoint(host: String, port: Int)
