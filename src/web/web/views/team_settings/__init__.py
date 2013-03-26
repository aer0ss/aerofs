def includeme(config):
    config.add_route('team_settings', '/admin/settings')
    # Use a short and easy to remember link since non-admins need to email the
    # link to their admins when necessary.
    config.add_route('start_subscription', '/upgrade')