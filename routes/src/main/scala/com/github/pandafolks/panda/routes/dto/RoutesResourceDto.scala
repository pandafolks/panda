package com.github.pandafolks.panda.routes.dto

final case class RoutesResourceDto(
                                    mappers: Option[Map[String, MapperRecordDto]] = Option.empty,
                                    prefixes: Option[Map[String, String]] = Option.empty
                                  )

final case class MapperRecordDto(mapping: Mapping, method: Option[String] = Option.empty)

final case class Mapping(value: Either[String, Map[String, Mapping]])

//{
//  "mappers":{
//  "/cars/rent":{
//  "mapping": {
//  "property1" : {"/some/path": "planes"},
//  "property2": "some2/path"
//},
//  "method":"delete"
//}
//},
//  "prefixes":{
//  "cars":"api/v1"
//}
//}


//{
//  "mappers":{
//  "/cars/rent":{
//  "mapping":"cars",
//  "method":"delete"
//},
//  "/cars/{{id}}/buy":{
//  "mapping":"cars"
//}
//},
//  "prefixes":{
//  "cars":"api/v1"
//}
//}