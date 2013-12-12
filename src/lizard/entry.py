# Nothing in particular ties us to cherrypy, but it beats the werkzeug wsgi
# server hand over fist and is pure-python.
# We could always run under uwsgi if we wanted to
from cherrypy import wsgiserver
from werkzeug.contrib.fixers import ProxyFix

from lizard import app, migrate_database

# We run behind an nginx proxy, so we need to fix up the WSGI environ
# to use assorted header data to perform external URL construction.
# Note that it is DANGEROUS to set this if you're NOT running behind a proxy
# because the site will blindly trust the incoming headers.
app.wsgi_app = ProxyFix(app.wsgi_app)

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
