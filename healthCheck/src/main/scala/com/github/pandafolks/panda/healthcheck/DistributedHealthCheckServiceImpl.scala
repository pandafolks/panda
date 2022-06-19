package com.github.pandafolks.panda.healthcheck

import com.github.pandafolks.panda.participant.ParticipantsCache
import com.github.pandafolks.panda.participant.event.ParticipantEventService

/**
 * This is a distributed implementation of [[HealthCheckService]].
 * Distributed in this scenario means it supports by default multi-node Panda configurations and makes use
 * of such configurations in terms of efficiency and splitting health check calls across multiple nodes.
 */
final class DistributedHealthCheckServiceImpl(private val participantEventService: ParticipantEventService,
                                              private val participantsCache: ParticipantsCache,
                                             )(private val healthCheckConfig: HealthCheckConfig) extends HealthCheckService {
}
