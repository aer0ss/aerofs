from web.util import add_routes


def includeme(config):
    routes = [
        'json_token',
        'json_new_token',
        'files',
    ]
    add_routes(config, routes)
