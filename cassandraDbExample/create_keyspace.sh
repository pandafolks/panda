#!/bin/bash


echo "CREATE KEYSPACE panda WITH replication = { 'class':
'SimpleStrategy', 'replication_factor': '2' } AND durable_writes = true; " > create_keyspace.cql

docker cp create_keyspace.cql cassandradb_cassandra-seed_1:/

docker exec -i cassandradb_cassandra-seed_1 cqlsh -f create_keyspace.cql
