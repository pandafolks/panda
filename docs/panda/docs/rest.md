# Rest Documentation

In order to communicate with and maintain the Panda gateway, there is a REST API provided.
<br /><br />

## Gateway entrypoint
`gateway/src/main/scala/com/github/pandafolks/panda/gateway/ApiGatewayRouting.scala`
<br /><br />

All REST calls targeted against provided routes need to go through this endpoint. <br />
Supported methods: ```GET```,```POST```,```PUT```,```PATCH```,```DELETE```. 
Everything after ```gateway``` keyword will be resolved and dispatched to one of the routes if the match was found.
There is no authorization check. <br /><br />
**Status codes:** <br />
- **404** - if there is either no matching route found or no available participant capable to serve the request at the given time existed<br />
- **everything else** - comes directly from the participants' responses

```shell
gateway/...
``` 
---
<br />

## Authentication endpoints
##### *User management endpoints.* 
`user/src/main/scala/com/github/pandafolks/panda/user/AuthRouting.scala`
<br /><br />

The endpoint is used for acquiring authentication tokens that are needed in the Panda maintaining operations like adding new participants or modifying routes. <br />
**Request payload example:**
```json 
{
  "username": "admin",
  "password": "admin"
} 
```
**Response payload example:**
```text 
a6f32febacfa21aba3b00be666197b3d8d3157ff-1665825498141-c6d6680e-0a02-44ce-b58e-ffd22e3b31d5
```
**Status codes:** <br />
- **200** - the user was successfully found <br />
- **401** - the user cannot be recognized

```shell
[POST] api/v1/auth/login
```
---
The endpoint is used for removing the user with provided credentials. <br />
**Request payload example:**
```json 
{
  "username": "admin",
  "password": "admin"
} 
```
**Status codes:** <br />
- **204** - the user was successfully removed <br />
- **401** - the user cannot be recognized

```shell
[DELETE] api/v1/auth/destroy
```
---

The endpoint is used for creating a new user with provided credentials. The endpoint needs authentication - in other words, there needs to be passed an authentication token of the existing user. This implies that new users can be created only by existing ones. <br />
**Request payload example:**
```json 
{
  "username": "admin",
  "password": "admin"
} 
```
**Status codes:** <br />
- **201** - the user was successfully created <br />
- **409** - the user with requested username already exists <br />
- **400** - any error during creation occurred

```shell
[POST] api/v1/auth/register
```
---
<br />

## Participants endpoints
##### *Endpoints used for participants (serves the requests are routed to) management. All calls targeting these endpoints are required to be authenticated.*
`participant/src/main/scala/com/github/pandafolks/panda/participant/ParticipantsRouting.scala`
<br /><br />

Returns registered groups (unique types of services). <br   />
**Response payload example:**
```json 
[
    {
        "name": "cars"
    },
    {
        "name": "planes"
    }
]
```
**Status codes:** <br />
- **200** - response successfully returned<br />
- **403** - the user does not have access to this resource<br />
- **404** - there isn't any registered group

```shell
[GET] api/v1/auth/groups
```
---

Returns all participants (registered services). It is possible to optional filtering out results by the participants' current status.
**Response payload example:**
```json 
[
    {
        "host": "localhost",
        "port": 3000,
        "group": {
            "name": "cars"
        },
        "identifier": "cars-one",
        "healthcheckInfo": {
            "path": "/api/v1/hb"
        },
        "status": {
            "NotWorking": {}
        },
        "health": {
            "Unhealthy": {}
        }
    },
    {
        "host": "localhost",
        "port": 3001,
        "group": {
            "name": "cars"
        },
        "identifier": "cars-two",
        "healthcheckInfo": {
            "path": "api/v1/hb"
        },
        "status": {
            "Working": {}
        },
        "health": {
            "Unhealthy": {}
        }
    },
    {
        "host": "localhost",
        "port": 4000,
        "group": {
            "name": "planes"
        },
        "identifier": "planes-one",
        "healthcheckInfo": {
            "path": "healthcheck"
        },
        "status": {
            "Working": {}
        },
        "health": {
            "Healthy": {}
        }
    }
]
```
**Status codes:** <br />
- **200** - response successfully returned<br />
- **403** - the user does not have access to this resource<br />
- **404** - there aren't any results
```shell
[GET] api/v1/auth/participants?filter=[all/working/healthy]
```
---

Returns all participants (registered services) belonging to a specified group (unique types of services). 
It is possible to optional filtering out results by the participants' current status. <br />
**Response payload example:**
```json 
[
    {
        "host": "localhost",
        "port": 3000,
        "group": {
            "name": "cars"
        },
        "identifier": "cars-one",
        "healthcheckInfo": {
            "path": "/api/v1/hb"
        },
        "status": {
            "Working": {}
        },
        "health": {
            "Unhealthy": {}
        }
    },
    {
        "host": "localhost",
        "port": 3001,
        "group": {
            "name": "cars"
        },
        "identifier": "cars-two",
        "healthcheckInfo": {
            "path": "api/v1/hb"
        },
        "status": {
            "Working": {}
        },
        "health": {
            "Unhealthy": {}
        }
    }
]
```
**Path params:** <br />
- **group_name** - name of the group for which participants will be returned <br />

**Status codes:** <br />
- **200** - response successfully returned<br />
- **403** - the user does not have access to this resource<br />
- **404** - there aren't any results
```shell
[GET] api/v1/auth/participants/{group_name}?filter=[all/working/healthy]
```
---

Creates new participants. The required fields are `host`, `port`, and `groupName`. The `identifier` needs to be unique across the whole application.<br />

**Request payload example:**
```json 
[
   {
      "identifier":"cars-three",
      "host":"localhost",
      "port":3000,
      "groupName":"cars",
      "working":true,
      "healthcheckRoute":"/api/v1/hb"
   },
   {
      "identifier":"planes-one",
      "host":"localhost",
      "port":4000,
      "groupName":"planes",
      "working":true
   }
]
```
**Response payload example:**
```json 
{
    "message": "Created successfully 1 participants out of 2 requested",
    "successfulParticipantIdentifiers": [
        "cars-three"
    ],
    "errors": [
        "[AlreadyExists$]: Participant with identifier \"planes-one\" already exists"
    ]
}
```
**Defaults (auto generated if omitted in the payload):** <br />
- **identifier** - `"{host}-{port}-{groupName}"` <br />
- **working** - `true` <br />
- **healthcheckRoute** - `"/healthcheck"` <br />

  **Status codes:** <br />
- **200** - creation request performed<br />
- **403** - the user does not have access to this resource<br />
```shell
[POST] api/v1/auth/participants
```
---

Updates participants' properties. Participant recognition is made based on the identifier.
Only those properties that were contained in the payload will be updated.

**Request payload example:**
```json 
[
   {
      "identifier":"cars-three",
      "host":"localhost",
      "port":3003,
      "groupName":"cars",
      "working":true,
      "healthcheckRoute":"/api/v1/hb"
   },
   {
      "identifier":"planes-two",
      "host":"110.110.110"
   }
]
```
**Response payload example:**
```json 
{
    "message": "Modified successfully 1 participants out of 2 requested",
    "successfulParticipantIdentifiers": [
        "cars-three"
    ],
    "errors": [
        "[NotExists$]: Participant with identifier \"planes-two\" does not exist"
    ]
}
```
**Defaults (auto generated if omitted in the payload):** <br />
- **identifier** - `"{host}-{port}-{groupName}"` (if the participant identifier is auto generated, this is impossible to modify none of the `host`, `port`, `groupName`)<br />

  **Status codes:** <br />
- **200** - update request performed<br />
- **403** - the user does not have access to this resource<br />
```shell
[PUT] api/v1/auth/participants
```
---

Removes participants with delivered identifiers.

**Request payload example:**
```json 
["cars-one", "cars-two", "planes-four"]
```
**Response payload example:**
```json 
{
    "message": "Removed successfully 2 participants out of 3 requested",
    "successfulParticipantIdentifiers": [
        "cars-one",
        "cars-two"
    ],
    "errors": [
        "[NotExists$]: Participant with identifier \"planes-four\" does not exist"
    ]
}
```
**Status codes:** <br />
- **200** - removal request performed<br />
- **403** - the user does not have access to this resource<br />
```shell
[DELETE] api/v1/auth/participants
```
---
  

