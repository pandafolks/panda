package com.github.mattszm.panda.db

import cats.effect.Resource
import com.github.mattszm.panda.participant.event.ParticipantEvent
import com.github.mattszm.panda.sequence.Sequence
import com.github.mattszm.panda.user.User
import com.github.mattszm.panda.user.token.Token
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task

trait DbAppClient {

  def getParticipantEventsAndSequencesConnection: Resource[Task, (CollectionOperator[ParticipantEvent], CollectionOperator[Sequence])]

  def getUsersWithTokensConnection: Resource[Task, (CollectionOperator[User], CollectionOperator[Token])]
}
