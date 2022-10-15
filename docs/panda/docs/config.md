# Application Config

This is an example configuration that you can run Panda instances with. <br />
`bootstap/src/main/resources/application.conf`
<br /><br />

```conf
app-server {
    # This is a Http4sEmberServerConfig
    listen-address = "0.0.0.0"
    listen-port = 8080
    max-connections = 2048
    idle-timeout = "60.seconds"
    shutdown-timeout = "30.seconds"
}

gateway {
    load-balancer-algorithm = "hash"                # Available: round-robin, random, hash
    load-balancer-retries = 9                       # Optional as this is only applicable to the hash-based load balancer, other load balancers will discard this configuration and will hit all possible participants in the worst case
}

gateway-client {
    # This is a Http4sEmberClientConfig responsible for configuration of the Http client used only for gateway requests.
    max-total = 200                                 # Sets the connection pool's total maximum number of idle connections.
    idle-time-in-pool = "30.seconds"                # Sets the connection pool's maximum time a connection can be idle. The timeout starts when a connection is returned the the pool, and reset when it is borrowed.
    chunk-size = 32768                              # Sets the max `chunkSize` in bytes to read from sockets at a time.
    max-response-header-size = 4096                 # Sets the max size in bytes to read while parsing response headers.
    idle-connection-time = "20.seconds"             # Sets the idle timeout on connections. The timeout is reset with each read or write.
    timeout = "30.seconds"                          # Sets the header receive timeout on connections.
    check-endpoint-identification = "true"          # Sets whether or not to force endpoint authentication/verification on the `TLSContext`. Enabled by default. When enabled the server's identity will be checked against the server's certificate during SSL/TLS handshaking. This is important to avoid man-in-the-middle attacks by confirming server identity against their certificate.
}

internal-client {
    # This is a Http4sEmberClientConfig responsible for configuration of the Http client used for any internal communication including healthcheck requests directed to the participants.
    max-total = 100                                 # Sets the connection pool's total maximum number of idle connections.
    idle-time-in-pool = "30.seconds"                # Sets the connection pool's maximum time a connection can be idle. The timeout starts when a connection is returned the the pool, and reset when it is borrowed.
    chunk-size = 32768                              # Sets the max `chunkSize` in bytes to read from sockets at a time.
    max-response-header-size = 4096                 # Sets the max size in bytes to read while parsing response headers.
    idle-connection-time = "2.seconds"              # Sets the idle timeout on connections. The timeout is reset with each read or write.
    timeout = "3.seconds"                           # Sets the header receive timeout on connections.
    check-endpoint-identification = "true"          # Sets whether or not to force endpoint authentication/verification on the `TLSContext`. Enabled by default. When enabled the server's identity will be checked against the server's certificate during SSL/TLS handshaking. This is important to avoid man-in-the-middle attacks by confirming server identity against their certificate.
}

db {
    contact-points = [
        {
            host = "127.0.0.1"
            port = 27017
        },
    ]
    username = "pandaUser"
    password = "pandaPassword"
    mode = "single"

;     connection-string="mongodb+srv://username:password@URL.../panda"  # The connection string can be used - instead of all the above properties. If both connection-string and contact-points are defined, the connection-string has higher priority.

    db-name = "panda"                               # Mandatory name of the database (even if the one in connection-string specified)
}

consistency {
    full-consistency-max-delay = 10                 # The maximum number of seconds after which the instance data will be fully consistent
}

health-check-config {
    calls-interval = 5                              # The interval between two health checks in seconds
    number-of-failures-needed-to-react = 3          # The number of allowed failed health checks. After reaching the specified number of fails, the participant will be marked as `Disconnected`
}

auth-tokens {
    time-to-live = 3600                             # Number of seconds before authentication tokens expire
}

init-user {                                         # If there is no user in the database, a default one will be created
    username = "admin"
    password = "admin"
}

``` 