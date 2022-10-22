# Documentation
<i>This page is incomplete - work in progress!</i> 

## Gateway traffic logging
The full logging configuration can be found inside `bootstap/src/main/resources/logback.xml` file.

By default, all gateway traffic is logged out to the `gateway_traffic.log`.
The logs contain both request and response headers. However, they do not contain bodies.