# Nothing in particular ties us to cherrypy, but it beats the werkzeug wsgi
# server hand over fist and is pure-python.
# We could always run under uwsgi if we wanted to
from cherrypy import wsgiserver
from lizard import app

d = wsgiserver.WSGIPathInfoDispatcher({'/': app})
server = wsgiserver.CherryPyWSGIServer(('127.0.0.1', 8588), d)
server.shutdown_timeout = .1

if __name__ == "__main__":
    try:
        server.start()
    except KeyboardInterrupt:
        server.stop()
