#!env/bin/python
import time
import sys
from lizard import create_app, migrate_database

import redis
from flask_kvsession import KVSessionExtension
from simplekv.memory.redisstore import RedisStore

internal = len(sys.argv) > 1 and sys.argv[1] == "internal"

app = create_app(internal)
migrate_database(app)

store = RedisStore(redis.StrictRedis())
KVSessionExtension(store, app)
app.run(debug=True, port=4444)
