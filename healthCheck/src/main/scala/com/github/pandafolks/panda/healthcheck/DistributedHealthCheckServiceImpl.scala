package com.github.pandafolks.panda.healthcheck

import com.github.pandafolks.panda.participant.ParticipantsCache
import com.github.pandafolks.panda.participant.event.ParticipantEventService

final class DistributedHealthCheckServiceImpl(private val participantEventService: ParticipantEventService,
                                              private val participantsCache: ParticipantsCache,
                                             )(private val healthCheckConfig: HealthCheckConfig) extends HealthCheckService {
}
