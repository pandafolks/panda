package com.github.pandafolks.panda.routes

import org.http4s.Uri.Path

final case class GroupInfo(group: Group, prefix: Path, isPocket: Boolean = false)
//todo mszmal: idea -> there should be no group, but Either[Group, MappingContent] ???
//todo mszmal: idea 2 -> rename GroupInfo to RouteInfo ??
