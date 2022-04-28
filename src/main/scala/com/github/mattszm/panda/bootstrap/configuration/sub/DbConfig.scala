package com.github.mattszm.panda.bootstrap.configuration.sub

final case class DbConfig(
                         contactPoints: List[String],
                         username: String,
                         password: String,
                         keySpace: String,
                         requestTimeout: Long,
                         connectionInitQueryTimeout: Long,
                         loadBalancingLocalDataCenter: String,
                         reconnectionBaseDelayInSeconds: Long,
                         heartbeatInterval: String,
                         heartbeatTimeout: String
                         )
