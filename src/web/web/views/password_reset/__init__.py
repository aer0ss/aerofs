def includeme(config):
    config.add_route('request_password_reset', '/request_password_reset')
    config.add_route('password_reset', '/password_reset')
