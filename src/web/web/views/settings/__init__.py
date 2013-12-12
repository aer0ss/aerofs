from web.util import add_routes

def includeme(config):
    config.add_route('access_tokens', 'apps')

    additional_routes = [
        'settings',
        'json_send_password_reset_email',
        'json_delete_access_token',
        'json_set_full_name'
    ]
    add_routes(config, additional_routes)
