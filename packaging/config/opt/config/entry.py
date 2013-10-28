from cherrypy import wsgiserver
from main import app

d = wsgiserver.WSGIPathInfoDispatcher({'/': app})
server = wsgiserver.CherryPyWSGIServer(('127.0.0.1', 5434), d)
server.shutdown_timeout = .1

if __name__ == '__main__':
    try:
        server.start()
    except KeyboardInterrupt:
        server.stop()
