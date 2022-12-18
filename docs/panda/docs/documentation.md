# Documentation
<i>This page is incomplete - work in progress!</i>

---
## Gateway traffic logging
The full logging configuration can be found inside `bootstap/src/main/resources/logback.xml` file.

By default, all gateway traffic is logged out to the `gateway_traffic.log`.
The logs contain both request and response headers. However, they do not contain bodies.

If the Panda user wants to send the logs to some remote service, the `ch.qos.logback.classic.net.SocketAppender` appender can be used.
More information can be found at this [link](https://logback.qos.ch/manual/appenders.html#SocketAppender).
---
## Marking Participants as TurnedOff and/or Removed if they are Unhealthy for a while

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
<i>Check the whole [Panda config](/config).</i>

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