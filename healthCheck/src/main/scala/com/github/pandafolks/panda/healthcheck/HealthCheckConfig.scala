package com.github.pandafolks.panda.healthcheck

final case class HealthCheckConfig(
                                    callsInterval: Int,
                                    numberOfAllowedFails: Int,
                                  )
