package com.github.pandafolks.panda.db

import cats.effect.Resource
import com.github.pandafolks.panda.healthcheck.UnsuccessfulHealthCheck
import com.github.pandafolks.panda.participant.event.ParticipantEvent
import com.github.pandafolks.panda.routes.entity.{Mapper, Prefix}
import com.github.pandafolks.panda.user.User
import com.github.pandafolks.panda.user.token.Token
import com.pandafolks.mattszm.panda.sequence.Sequence
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task

trait DbAppClient {

  def getSettings: Any

  def getDbName: String

  def getParticipantEventsAndSequencesConnection: Resource[Task, (CollectionOperator[ParticipantEvent], CollectionOperator[Sequence])]

  def getUsersWithTokensConnection: Resource[Task, (CollectionOperator[User], CollectionOperator[Token])]

  def getUnsuccessfulHealthCheckConnection: Resource[Task, CollectionOperator[UnsuccessfulHealthCheck]]

  def getMappersAndPrefixesConnection: Resource[Task, (CollectionOperator[Mapper], CollectionOperator[Prefix])]
}
