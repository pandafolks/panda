#!/bin/bash

mongo <<EOF
   var cfg = {
        "_id": "rs",
        "version": 1,
        "members": [
            {
                "_id": 0,
                "host": "mongo-0.mongo:27017",
                "priority": 2
            },
            {
                "_id": 1,
                "host": "mongo-1.mongo:27017",
                "priority": 0
            },
            {
                "_id": 2,
                "host": "mongo-2.mongo:27017",
                "priority": 0
            }
        ]
    };
    rs.initiate(cfg, { force: true });
    //rs.reconfig(cfg, { force: true });
    rs.status();
EOF
sleep 10

mongo <<EOF
   use admin;
   admin = db.getSiblingDB("admin");
   admin.createUser(
     {
	user: "admin",
        pwd: "password",
        roles: [ { role: "root", db: "admin" } ]
     });
     db.getSiblingDB("admin").auth("admin", "password");
     rs.status();
EOF
sleep 2

mongo -u admin -p 'password' <<EOF
   use panda;
   panda = db.getSiblingDB("panda");
   panda.createUser(
     {
	      user: "pandaUser",
        pwd: "pandaPassword",
        roles: [ { role: "readWrite", db: "panda" } ]
     });
     db.getSiblingDB("panda").auth("pandaUser", "pandaPassword");
     rs.status();
EOF

#docker exec mongo-0.mongo /scripts/setup.sh