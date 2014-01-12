from tornado.wsgi import WSGIContainer
from tornado.httpserver import HTTPServer
from tornado.ioloop import IOLoop
from rocklog import app
import tornado

http_server = HTTPServer(WSGIContainer(app))
http_server.bind(8000)
http_server.start(4)
IOLoop.instance().start()
