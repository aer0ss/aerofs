from web.util import add_routes

routes = [
    'report-problems',
]

def includeme(config):
    add_routes(config, routes)
