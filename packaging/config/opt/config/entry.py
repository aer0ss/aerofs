#!/usr/bin/env python
import logging

from flask import request
from cherrypy import wsgiserver

from main import app

# configure basic logging
LOG_FILE = '/var/log/aerofs/config.log'
file_handler = logging.FileHandler(LOG_FILE)
file_handler.setLevel(logging.DEBUG)
formatter = logging.Formatter('[%(asctime)s] [%(name)s %(levelname)s] %(message)s',
                              datefmt="%Y-%m-%d %H:%M:%S %z")
file_handler.setFormatter(formatter)
app.logger.addHandler(file_handler)
app.logger.setLevel(logging.DEBUG)

# Log each request's method and path, and the status code returned
@app.after_request
def log_request(resp):
    app.logger.info("{} {} {}".format(resp.status_code, request.method, request.path))
    return resp

d = wsgiserver.WSGIPathInfoDispatcher({'/': app})
server = wsgiserver.CherryPyWSGIServer(('127.0.0.1', 5434), d)
server.shutdown_timeout = .1

if __name__ == '__main__':
    try:
        app.logger.info("Starting up...")
        server.start()
    except KeyboardInterrupt:
        server.stop()
