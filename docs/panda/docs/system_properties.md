# Custom System Properties

---
### panda.user.token.key
A private key used to sign users' authentication tokens.

If not present, the value present in [tokenKey.txt](https://github.com/pandafolks/panda/blob/master/user/src/main/resources/tokenKey.txt) from the user's module resources is taken.
If the [tokenKey.txt](https://github.com/pandafolks/panda/blob/master/user/src/main/resources/tokenKey.txt) file does not exist, the random key is generated during Panda node startup.

It is highly required to use the same private key value across all Panda nodes.

<b>Example:</b>
```shell
-Dpanda.user.token.key=5ck4kBO45606H25YUZ1f
```

---
### panda.consistent.hashing.state.positions.per.participant
Used only when `hash` based load balancer is used (Inside [com/github/pandafolks/panda/loadbalancer/ConsistentHashingState.scala](https://github.com/pandafolks/panda/blob/master/loadBalancer/src/main/scala/com/github/pandafolks/panda/loadbalancer/ConsistentHashingState.scala)). 
A number of points on the consistent hashing circle for a single participant. The higher the number is the more evenly the requests  will be spread but the performance of the adding to the circle operation will drop.

The default value is <b>20</b>.

<b>Example:</b>
```shell
-Dpanda.consistent.hashing.state.positions.per.participant=30
```

---
### panda.consistent.hashing.state.clear.empty.groups.interval
Used only when `hash` based load balancer is used (Inside [com/github/pandafolks/panda/loadbalancer/ConsistentHashingState.scala](https://github.com/pandafolks/panda/blob/master/loadBalancer/src/main/scala/com/github/pandafolks/panda/loadbalancer/ConsistentHashingState.scala)). 
A number of hours between each run of the background job which is responsible for clearing empty groups inside the <b>ConsistentHashingState#usedPositionsGroupedByGroup</b> in order to reduce memory overhead. If the value is smaller or equal to '0' the background job won't be launched.

The default value is <b>12</b>.

<b>Example:</b>
```shell
-Dpanda.consistent.hashing.state.clear.empty.groups.interval=24
```

---
