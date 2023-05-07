# Contributing to Panda

Hello everyone, this project was created mainly in order to evolve contributors' skills and have fun. 
If you don't have either scala or distributed systems experience, that's all right, you can play around here! 
We don't have deadlines or any other pressure, so the quality of the solutions is a crucial factor.

---
## Code of Conduct
People are expected to follow the [Scala Code of Conduct](https://www.scala-lang.org/conduct/)

---
## General Workflow

- Make sure you can license your work under Apache 2.0 
- Pick a ticket from the issues section. Don't hesitate to ask clarifying questions if something is unclear.
- If you don't have write access to the repository, you should do
   your work in a local branch of your own fork and then submit a pull
   request. If you do have write access to the repository, never work
   directly on master.
- If you add new methods to services, remember about adding Docs. We are trying to avoid comments inside the code. 
   However, if some part is more tricky than usual, a short explanation is advised.
- Once you finish work on your Pull Request, make sure both unit and integration tests are passing:
```sbtshell
sbt test
```
```sbtshell
sbt it:test
```
- Make sure the code is formatted with:
```shell
sbt scalafmtAll
```
- Resolve conflicts with the master branch (if any).
- Submit a Pull Request.
- Anyone can comment on a Pull Request, and you are expected to
   answer questions or to incorporate feedback.
- Once the Pull Request is approved, owners will take care of merging.

Despite we try to work along with the issues tracker. Once you see something that may be done in a better way, feel 
free to make a Pull Request with improvement.

---
## Hints

### Application entry point
[bootstap/src/main/scala/com/github/pandafolks/panda/bootstrap/App.scala](https://github.com/pandafolks/panda/blob/master/bootstrap/src/main/scala/com/github/pandafolks/panda/bootstrap/App.scala)

### Current authentication model
`http://localhost:8080/api/v1/auth/register` endpoint creates a new user. However, it is accessible only to authenticated users.
Because of that, new users can be only created by existing ones and that's why `application.conf` is prefilled with `init-user`.
`init-user` will be created during app startup only if there is no other user present in the DB.

### Dependencies
Inside `exampleExternalDependencies` directory you could find docker files that runs required components.
At this moment panda requires:
1. MongoDB

### Service examples used for manual testing
As Panda is an API gateway, it is useful to have some services it can communicate to.
Inside [exampleExternalDependencies/auxiliaryServices](https://github.com/pandafolks/panda/tree/master/exampleExternalDependencies/auxiliaryServices) directory you may find [docker-compose.yml](https://github.com/pandafolks/panda/blob/master/exampleExternalDependencies/auxiliaryServices/docker-compose.yml) file which runs some simple services.
You could register them inside Panda by running [registerInsidePanda.sh](https://github.com/pandafolks/panda/blob/master/exampleExternalDependencies/auxiliaryServices/registerInsidePanda.sh) script. 

### Managing dependencies
Panda provides `Makefile` file for managing project dependencies and tasks in the easy way. To see list of useful 
commands run this in the repository root:
```bash
make help
```

### Creating executable jar
```sbtshell
sbt assembly
```
You would find `panda.jar` inside your `/target/scala-2.13` directory.
You can run it with `java -jar panda.jar`.

### Using your own config file
By default, Panda will use [application.conf](https://github.com/pandafolks/panda/blob/master/bootstrap/src/main/resources/application.conf) config from [bootstap/src/main/resources](https://github.com/pandafolks/panda/tree/master/bootstrap/src/main/resources). 
Feel free to create your own config, you could use it inside panda by adding VM option: 
```sbtshell
-Dconfig.resource=application.conf
```