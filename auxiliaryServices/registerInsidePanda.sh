#!/bin/sh

TOKEN=$(curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json"  \
  -d '{"username": "admin", "password": "admin"}')

curl -X POST http://localhost:8080/api/v1/participants \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '[{"identifier": "cars-one", "host": "localhost", "port": 3000, "groupName": "cars", "working": true, "healthcheckRoute": "api/v1/hb"}, {"identifier": "cars-two", "host": "localhost", "port": 3001, "groupName": "cars", "working": true, "healthcheckRoute": "api/v1/hb"}, {"identifier": "planes-one", "host": "localhost", "port": 4000, "groupName": "planes", "working": true}]'
