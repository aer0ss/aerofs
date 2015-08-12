import os.path
from cherrypy import wsgiserver
from pyramid.paster import get_app
from pyramid.paster import setup_logging

bunker = get_app('bunker.ini', 'main')
setup_logging('bunker.ini')

if __name__ == "__main__":
    server = None
    try:
        server = wsgiserver.CherryPyWSGIServer(('0.0.0.0', 8484), bunker)
        server.shutdown_timeout = .1
        server.start()
    except KeyboardInterrupt:
        if server is not None: server.stop()
