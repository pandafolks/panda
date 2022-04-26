#!/bin/bash


echo "CREATE KEYSPACE IF NOT EXISTS panda WITH replication = { 'class':
'SimpleStrategy', 'replication_factor': '1' } AND durable_writes = true; " > create_keyspace.cql

docker cp create_keyspace.cql cassandradbexample_cassandra_1:/

docker exec -i cassandradbexample_cassandra_1  cqlsh -u cassandra -p cassandra -f create_keyspace.cql
