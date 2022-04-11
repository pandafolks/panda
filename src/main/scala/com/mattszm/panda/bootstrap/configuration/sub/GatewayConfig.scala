package com.mattszm.panda.bootstrap.configuration.sub

final case class GatewayConfig(
                                mappingFile: String,
                                loadBalanceAlgorithm: LoadBalanceAlgorithm
                              )

sealed trait LoadBalanceAlgorithm
case object RoundRobin extends LoadBalanceAlgorithm
