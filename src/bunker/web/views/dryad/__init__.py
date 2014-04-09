from web.util import add_routes

routes = [
    'report-problem',
    'json-submit-report',
    'json-get-users',
]

def includeme(config):
    add_routes(config, routes)
