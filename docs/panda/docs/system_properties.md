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
### panda.main.log.file.name
The name of the main log file. The rolling policy of the main log file is SizeAndTimeBasedRollingPolicy.

The default value is <b>panda</b>.

<b>Example:</b>
```shell
-Dpanda.main.log.file.name=panda_logs
```

---
### panda.main.log.max.file.size
The size limit of the single panda log file.

The default value is <b>100MB</b>.

<b>Example:</b>
```shell
-Dpanda.main.log.max.file.size=512MB
```

---
### panda.main.log.max.history.in.days
Specifies how many days the history logs of the main panda log will be kept.

The default value is <b>60</b>.

<b>Example:</b>
```shell
-Dpanda.main.log.max.history.in.days=30
```

---
### panda.main.log.total.size.cap
Sets the size limit of the whole main panda log.

The default value is <b>10GB</b>.

<b>Example:</b>
```shell
-Dpanda.main.log.total.size.cap=50GB
```

---
### panda.gateway.traffic.log.file.name
The name of the gateway traffic log file. The rolling policy of the main log file is SizeAndTimeBasedRollingPolicy.

The default value is <b>gateway_traffic</b>.

<b>Example:</b>
```shell
-Dpanda.gateway.traffic.log.file.name=gateway
```

---
### panda.gateway.traffic.log.max.file.size
The size limit of the single gateway traffic log file.

The default value is <b>100MB</b>.

<b>Example:</b>
```shell
-Dpanda.gateway.traffic.log.max.file.size=512MB
```

---
### panda.gateway.traffic.log.max.history.in.days
Specifies how many days the history logs of gateway traffic log will be kept.

The default value is <b>60</b>.

<b>Example:</b>
```shell
-Dpanda.gateway.traffic.log.max.history.in.days=30
```

---
### panda.gateway.traffic.log.total.size.cap
Sets the size limit of the whole gateway traffic log.

The default value is <b>10GB</b>.

<b>Example:</b>
```shell
-Dpanda.gateway.traffic.log.total.size.cap=50GB
```

---