from pyramid.paster import get_app
from pyramid.paster import setup_logging

application = get_app('production.ini', 'main')
setup_logging('production.ini')
