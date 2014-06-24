#!env/bin/python
import logging

from cherrypy import wsgiserver
from werkzeug.contrib.fixers import ProxyFix
from flask import request

from charlie import app

# Set up logging
LOG_FILE = '/var/log/charlie/charlie.log'
LOG_LEVEL = logging.DEBUG
file_handler = logging.FileHandler(LOG_FILE)
file_handler.setLevel(LOG_LEVEL)
formatter = logging.Formatter('[%(asctime)s] [%(name)s %(levelname)s] %(message)s',
                datefmt="%Y-%m-%d %H:%M:%S %z")
file_handler.setFormatter(formatter)
app.logger.addHandler(file_handler)
app.logger.setLevel(LOG_LEVEL)

# Log all requests
@app.after_request
def log_request(resp):
    app.logger.info("{} {} {} {}".format(request.remote_addr, resp.status_code, request.method, request.path))
    return resp

app.wsgi_app = ProxyFix(app.wsgi_app)

application = app.wsgi_app

if __name__ == "__main__":
    # host:port to listen on, if invoked with a python interpreter
    host = "127.0.0.1"
    port = 8701

    d = wsgiserver.WSGIPathInfoDispatcher({"/": app})
    server = wsgiserver.CherryPyWSGIServer((host, port), d)
    server.shutdown_timeout = .1
    try:
        server.start()
    except KeyboardInterrupt:
        server.stop()
