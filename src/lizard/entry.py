# Nothing in particular ties us to cherrypy, but it beats the werkzeug wsgi
# server hand over fist and is pure-python.
# We could always run under uwsgi if we wanted to
import sys
import logging

from cherrypy import wsgiserver
from werkzeug.contrib.fixers import ProxyFix
from flask import request

import redis
from flask_kvsession import KVSessionExtension
from simplekv.memory.redisstore import RedisStore

from lizard import create_app, migrate_database

internal = len(sys.argv) > 1 and sys.argv[1] == "internal"
port = 5001 if internal else 8588

store = RedisStore(redis.StrictRedis())
app = create_app(internal)
KVSessionExtension(store, app)

# Set up logging.
LOG_FILE = '/var/log/lizard/{}.log'.format("lizard-admin" if internal else "lizard")
LOG_LEVEL = logging.DEBUG
file_handler = logging.FileHandler(LOG_FILE)
file_handler.setLevel(LOG_LEVEL)
formatter = logging.Formatter('[%(asctime)s] [%(name)s %(levelname)s] %(message)s',
        datefmt="%Y-%m-%d %H:%M:%S %z")
file_handler.setFormatter(formatter)
app.logger.addHandler(file_handler)
app.logger.setLevel(LOG_LEVEL)

# Log all requests.
@app.after_request
def log_request(resp):
    app.logger.info("{} {} {} {}".format(request.remote_addr, resp.status_code, request.method, request.path))
    return resp

# We run behind an nginx proxy, so we need to fix up the WSGI environ
# to use assorted header data to perform external URL construction.
# Note that it is DANGEROUS to set this if you're NOT running behind a proxy
# because the site will blindly trust the incoming headers.
app.wsgi_app = ProxyFix(app.wsgi_app)

d = wsgiserver.WSGIPathInfoDispatcher({'/': app})
server = wsgiserver.CherryPyWSGIServer(('0.0.0.0', port), d)
server.shutdown_timeout = .1

if __name__ == "__main__":
    migrate_database(app)
    try:
        # And run the server.
        server.start()
    except KeyboardInterrupt:
        server.stop()
