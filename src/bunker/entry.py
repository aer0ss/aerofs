from cherrypy import wsgiserver
from pyramid.paster import get_app
from pyramid.paster import setup_logging

bunker = get_app('production.ini', 'main')
setup_logging('production.ini')

# FIXME (AG): load this from the config file
server = wsgiserver.CherryPyWSGIServer(('0.0.0.0', 8588), bunker)
server.shutdown_timeout = .1

if __name__ == "__main__":
    try:
        server.start()
    except KeyboardInterrupt:
        server.stop()
