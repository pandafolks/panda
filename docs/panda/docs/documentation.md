# Documentation
<i>This page is incomplete - work in progress!</i> 

## Gateway traffic logging
The full logging configuration can be found inside `bootstap/src/main/resources/logback.xml` file.

By default, all gateway traffic is logged out to the `gateway_traffic.log`.
The logs contain both request and response headers. However, they do not contain bodies.

If the Panda user wants to send the logs to some remote service, the `ch.qos.logback.classic.net.SocketAppender` appender can be used.
More information can be found at this [link](https://logback.qos.ch/manual/appenders.html#SocketAppender).