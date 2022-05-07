db.createUser(
    {
        user: "pandaUser",
        pwd: "pandaPassword",
        roles: [
            {
                role: "readWrite",
                db: "panda"
            }
        ]
    }
);
