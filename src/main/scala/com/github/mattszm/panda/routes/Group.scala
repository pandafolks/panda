package com.github.mattszm.panda.routes

import org.http4s.Uri.Path

final case class Group(name: String)

final case class GroupInfo(group: Group, prefix: Path)
