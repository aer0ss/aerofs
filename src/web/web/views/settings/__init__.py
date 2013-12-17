from web.util import add_routes

def includeme(config):
    config.add_route('access_tokens', 'apps')
    config.add_route('app_authorization', 'authorize')

    additional_routes = [
        'settings',
        'json_send_password_reset_email',
        'json_set_full_name',
        'json_delete_access_token',
    ]
    add_routes(config, additional_routes)
