# Rest Documentation

In order to communicate with and maintain the Panda gateway, there is a REST API provided.


## Gateway entrypoint
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
<br />

## Authentication endpoints
The endpoint is used for acquiring authentication tokens that are needed in the Panda maintaining operations like adding new participants or modifying routes. <br />
**Request payload example:**
```json 
{
  "username": "admin",
  "password": "admin"
} 
```
**Status codes:** <br />
- **200** - if the user was successfully found <br />
- **401** - if the user cannot be recognized

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
- **204** - if the user was successfully removed <br />
- **401** - if the user cannot be recognized

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
- **201** - if the user was successfully created <br />
- **409** - if the user with requested username already exists <br />
- **400** - if any error during creation occurred

```shell
[POST] api/v1/auth/register
```
---