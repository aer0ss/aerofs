# Nothing in particular ties us to cherrypy, but it beats the werkzeug wsgi
# server hand over fist and is pure-python.
# We could always run under uwsgi if we wanted to
from cherrypy import wsgiserver

from lizard import app, migrate_database

d = wsgiserver.WSGIPathInfoDispatcher({'/': app})
# TODO: make this localhost if not testing
server = wsgiserver.CherryPyWSGIServer(('0.0.0.0', 8588), d)
server.shutdown_timeout = .1

if __name__ == "__main__":
    migrate_database()
    try:
        # And run the server.
        server.start()
    except KeyboardInterrupt:
        server.stop()
