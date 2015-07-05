# Server port assignments

## Internal use only

**TODO: refere to the code that defines these ports, instead of duplicating the information here.**

```
localhost:3306 - mysql
localhost:6379 - redis
localhost:9002 - CA (python)
localhost:8000 - sanity/serverstatus backend
localhost:5434 - config service (python) backend
localhost:5436 - config service (sensitive) nginx frontend
localhost:8081 - web backend
localhost:8080 - tomcat (sp, identity, syncstat)
localhost:9019 - verkehr http
localhost:9079 - verkehr httpadmin
localhost:9293 - verkehr publish
localhost:25234 - verkehr admin
devman:
  localhost:9020 - http
  localhost:9021 - admin
```

## Publicly-reachable

```
nginx, proxying for:
  8080 - sanity frontend
  5435 - config service frontend (should probably be moved under web frontends)
  443 - browser-cert web/sp/api frontend
  4433 - aerofs-cert web/sp/api frontend
5222 - ejabberd [deprecated]
8888 - zephyr
8084 - havre (daemons connect here)
29438 - lipwig [used to be vk protobuf endpoint]
```

# Client port assignments

Note that clients can wind up using different ports through the use of the pb (portbase) file.

```
50193 (localhost) - client default portbase
60193 (localhost) - team server default portbase
```

```
pb + 3 : UI singleton (used to ensure only one instance of GUI will run per user)
```

The client also listens on random ephemeral TCP ports for the TCP tranport.
