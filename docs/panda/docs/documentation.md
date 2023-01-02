# Documentation
<i>This page is incomplete - work in progress!</i>

---
## <b>Gateway</b>

---
### Gateway traffic logging
The full logging configuration can be found inside `bootstap/src/main/resources/logback.xml` file.

By default, all gateway traffic is logged out to the `gateway_traffic.log`.
The logs contain both request and response headers. However, they do not contain bodies.

If the Panda user wants to send the logs to some remote service, the `ch.qos.logback.classic.net.SocketAppender` appender can be used.
More information can be found at this [link](https://logback.qos.ch/manual/appenders.html#SocketAppender).
---
## <b>Load Balancing</b>

---
### Highlights
Load balancing inside Panda makes sure to distribute the incoming load across available participants.
The flow is as follows:

1. Recognize the path within the request and decide if it's registered and supported inside Panda.
2. Find a group responsible for a path.
3. Pass the request to the load balancer.
4. Route the request to one of the healthy participants assigned to the found group.
5. Failover to another healthy participant if the previously chosen participant is unreachable.
6. Repeat the 5th step in case of failure.
<br/>

There are 3 supported load balancing algorithms:

- Round robin
- Random
- Hash based
<br />

All the timeouts for a single call made by load balancers are configurable within the [`gateway-client` config](/panda/config).
The important is that the failover mechanism is integrated with the timeouts, so reaching the timeout by the single participant will end up with the failover mechanism passing the request to another participant <i>(and automatically killing the previous request)</i>.

<br/>
<i>'group' might be the (micro)service type</i>

<i>'participant' is the single instance of the particular type</i>

---
### Round robin load balancer
This load balancer passes the requests to the participants in a one-by-one order.

In order to avoid the failover mechanism sending requests all the time to the same failing participant <i>(which is the worst-case scenario, where other participants are picked between failing request retry calls)</i>, the smart failover is implemented which picks the very first participant according to the Round robin order, but in case of failure retries with the next participant <i>(until tries with all available participants)</i> completely independently of the main order. As a result failover mechanism will never pass a specific request to the same participant more than once.

<b>How to configure:</b>
```shell
gateway {
    load-balancer-algorithm = "round-robin"   # Available: round-robin, random, hash
 }
```

<i>Known issue: [Expand the RoundRobinLoadBalancerImpl and RandomLoadBalancerImpl capabilities](https://github.com/pandafolks/panda/issues/116)</i>

---
### Random load balancer
This load balancer passes the requests to the participants by choosing them randomly.

In order to avoid the failover mechanism sending requests all the time to the same failing participant <i>(which is possible if failover candidates would be chosen randomly)</i>, the smart failover is implemented which generates the randomly shuffled queue of failover candidates that includes a single occurrence of all healthy participants. As a result failover mechanism will never pass a specific request to the same participant more than once and in the worst-case scenario will do that exactly once.

<b>How to configure:</b>
```shell
gateway {
    load-balancer-algorithm = "random"   # Available: round-robin, random, hash
 }
```

<b>Note:</b>
The load balancer tries to hit all available participants. This is an important feature in the worst-case scenario. However, because of that, the solution is relatively slow. The RandomLoadBalancer should be used only with a small number of participants. In other cases, either the RoundRobinLoadBalancer or HashLoadBalancer should be preferred.

<i>Known issue: [Expand the RoundRobinLoadBalancerImpl and RandomLoadBalancerImpl capabilities](https://github.com/pandafolks/panda/issues/116)</i>

---
### Hash based load balancer
This load balancer routes the request to the participant based on either the `X-Forwarded-For` header <i>(left most value - the origin client)</i> or client IP if the `X-Forwarded-For` header does not exist. As a result, all requests from a single client are routed to the same participant.

Because of the nature of the hashing, the client is stuck to the participant as long as there are no new participants within the group or one of the existing ones does not disappear. However, the implementation is using the consistent hashing approach, and because of that, only a small part of clients will be reassigned to a different participant in case of participants' health changes.

If the initially picked participant is unreachable, the failover mechanism will pick the failover candidate fully randomly, without guarantee of not asking the same participant more than once. The number of retries is configurable inside the [configuration file](/panda/config). Where 10 is the default number of retries.

<b>How to configure:</b>
```shell
gateway {
    load-balancer-algorithm = "hash"       # Available: round-robin, random, hash
    load-balancer-retries = 5              # Optional as this is only applicable to the hash-based load balancer, other load balancers will discard this configuration and will hit all possible participants in the worst case
 }
```

<b>Note:</b>
If the Panda works as a cluster of multiple nodes. The load balancer that stands in front of Panda nodes has to route all requests of a particular client to the same, constant node. This is because the consistent hashing circle is created and kept in memory per Panda node and there is no guarantee of client assignment to a particular participant among Panda nodes <i>(only within a single node)</i>.

---
### X-Forwarded-For header
Every Panda load balancer automatically adds the <b>X-Forwarded-For</b> header and helps you identify the IP address of a client. 
Because load balancers intercept traffic between clients and servers, your server access logs contain only the IP address of the load balancer. 
To see the IP address of the client, use the <b>X-Forwarded-For</b> request header. 
Panda's Load Balancers store the IP address of the client in the <b>X-Forwarded-For</b> request header and passes the header to your server. 
If the <b>X-Forwarded-For</b> request header is not included in the request, the load balancer creates one with the client IP address as the request value. 
Otherwise, the load balancer appends the client IP address to the existing header and passes the header to your server. 
The <b>X-Forwarded-For</b> request header may contain multiple IP addresses that are comma separated. 
The left-most address is the client IP where the request was first made. This is followed by any subsequent proxy identifiers, in a chain.

The <b>X-Forwarded-For</b> header takes the following form:
```shell
X-Forwarded-For: client-ip-address
```

---
## <b>Participants maintaining (Health checks)</b>

---
### Marking Participants as TurnedOff and/or Removed if they are Unhealthy for a while

This feature is part of the healthcheck functionality **(not documented yet)**.
The healthcheck functionality maintains the operational state of configured participants.
Based on this state, the decision whether the traffic should be routed to the particular participant is made.

The healtcheck functionality decides only if the instance is Healthy or Unhealthy and does not mark participants as TurnedOff or Removed on its own.
This can be achieved by the separate feature that can be configured via the configuration fie:

```conf
health-check-config {
    participant-is-marked-as-turned-off-delay = 120 # The number of seconds the participant is in the `Unhealthy` state after which the participant will be marked as not working by emitting `TurnedOff` event. If the value is not present or is smaller than 1 the feature is turned off. If turned on, the value is required to be smaller than participant-is-marked-as-removed-delay, otherwise, this setting will be discarded.
    participant-is-marked-as-removed-delay = 180    # The number of seconds the participant is in the `Unhealthy` state after which the participant will be marked as not working by emitting `Removed` event. If the value is not present or is smaller than 1 the feature is turned off.
    marked-as-not-working-job-interval = 30         # The number of seconds between the background job calls. The background job is responsible for marking participants as not working in alignment with `participant-is-marked-as-not-working-delay` and `participant-is-marked-as-removed-delay` properties. If the value is not present or is smaller than 1 the default is 30 seconds.
}
```
<i>Check the whole [Panda config](/panda/config).</i>

If the participant was marked as Unhealthy by the healthcheck functionality the specified number of seconds ago, it can be 
automatically marked as either Removed or TurnedOff. This feature can be turned on only if the healthcheck functionality is also turned on 
(so both properties `calls-interval` and `number-of-failures-needed-to-react` have to be bigger than 0).

This functionality is not distributed across many Panda nodes and is always performed by a single one. 
The failover is performed under the hood, so when the node responsible for this functionality is down or not responsive enough, 
the other node will take over and take care of performing this action.

This implemented functionality will detect only these participants that were healthy at least one time in the whole lifecycle. 
This addresses the flow of having preconfigured participants. The Panda use case is to have already configured participants that will be 
launched in the future or are launching at the moment - we do not want to clear them (mark them as TurnedOff/Removed) before they were ever operational.

---