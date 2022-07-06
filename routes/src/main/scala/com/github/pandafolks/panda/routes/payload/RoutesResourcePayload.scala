package com.github.pandafolks.panda.routes.payload

final case class RoutesResourcePayload(
                                        mappers: Option[Map[String, MapperRecordPayload]] = Option.empty,
                                        prefixes: Option[Map[String, String]] = Option.empty
                                      )

final case class MapperRecordPayload(mapping: MappingPayload, method: Option[String] = Option.empty)

final case class MappingPayload(value: Either[String, Map[String, MappingPayload]])

//{
//  "mappers":{
//  "/cars/rent":{
//  "mapping": {
//  "property1" : {"/some/path": "/planes/somepath1/param123"},
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