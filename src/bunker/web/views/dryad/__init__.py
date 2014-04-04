from web.util import add_routes

routes = [
    'report-problem',
    'json-submit-report'
]

def includeme(config):
    add_routes(config, routes)
