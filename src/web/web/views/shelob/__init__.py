from web.util import add_routes


def includeme(config):
    routes = [
        'json_token',
        'json_new_token',
        'files',
        'create_url',
        'set_url_expires',
        'remove_url_expires',
        'remove_url',
        'set_url_team_only',
        'set_url_password',
        'remove_url_password',
        'validate_url_password',
        'list_urls_for_store',
        'audit_link_download',
    ]
    add_routes(config, routes)
    config.add_route('get_url', 'l/{key}')
    config.add_route('get_url_info', 'url_info/{key}')
