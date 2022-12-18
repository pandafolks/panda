package com.github.pandafolks.panda.db

import cats.effect.Resource
import com.github.pandafolks.panda.healthcheck.UnsuccessfulHealthCheck
import com.github.pandafolks.panda.nodestracker.{Job, Node}
import com.github.pandafolks.panda.participant.event.ParticipantEvent
import com.github.pandafolks.panda.routes.entity.{Mapper, Prefix}
import com.github.pandafolks.panda.sequence.Sequence
import com.github.pandafolks.panda.user.User
import com.github.pandafolks.panda.user.token.Token
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task

trait DbAppClient {

  def getParticipantEventsAndSequencesConnection: Resource[Task, (CollectionOperator[ParticipantEvent], CollectionOperator[Sequence])]

  def getUsersWithTokensConnection: Resource[Task, (CollectionOperator[User], CollectionOperator[Token])]

  def getNodesConnection: Resource[Task, CollectionOperator[Node]]

  def getJobsConnection: Resource[Task, CollectionOperator[Job]]

  def getUnsuccessfulHealthCheckConnection: Resource[Task, CollectionOperator[UnsuccessfulHealthCheck]]

  def getMappersAndPrefixesConnection: Resource[Task, (CollectionOperator[Mapper], CollectionOperator[Prefix])]
}
