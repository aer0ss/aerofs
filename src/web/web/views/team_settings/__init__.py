def includeme(config):
    config.add_route('team_settings', '/admin/settings')
    config.add_route('json.set_authorization', '/admin/users/set_auth')
    config.add_route('json.user_lookup', '/admin/settings/user_lookup')
    config.add_route('json.get_admins', '/admin/settings/get_admins')
