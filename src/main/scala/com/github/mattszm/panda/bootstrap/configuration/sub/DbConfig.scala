package com.github.mattszm.panda.bootstrap.configuration.sub

final case class DbConfig(
                         host: String,
                         port: Int,
                         username: String,
                         password: String,
                         keySpace: String,
                         replicationFactor: Int,
                         connectTimeoutMillis: Int,
                         readTimeoutMillis: Int
                         )
