from cherrypy import wsgiserver
from pyramid.paster import get_app
from pyramid.paster import setup_logging

bunker = get_app('production.ini', 'main')
setup_logging('production.ini')

server = wsgiserver.CherryPyWSGIServer(('0.0.0.0', 4444), bunker)
server.shutdown_timeout = .1

if __name__ == "__main__":
    try:
        server.start()
    except KeyboardInterrupt:
        server.stop()
