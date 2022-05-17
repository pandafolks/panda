package com.github.mattszm.panda.routes

final case class Trees(get: Option[RoutesTree] = Option.empty, post: Option[RoutesTree] = Option.empty) // todo: support post and other methods
