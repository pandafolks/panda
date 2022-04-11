package com.github.mattszm.panda.participants

import com.github.mattszm.panda.routes.Group

final case class Participant(host: String, port: Int, group: Group, identifier: Option[String] = Option.empty)
