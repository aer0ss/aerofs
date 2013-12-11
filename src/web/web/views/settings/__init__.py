def includeme(config):
    config.add_route('access_tokens', 'apps')
    config.add_route('json_delete_access_token', 'json_delete_access_token')
