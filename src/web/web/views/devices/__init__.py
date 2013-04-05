def includeme(config):
    # The "devices" and "admin/team_servers" strings must be consistent with
    # BaseParam.java.
    config.add_route('my_devices', 'devices')
    config.add_route('user_devices', 'admin/devices')
    config.add_route('team_server_devices', 'admin/team_servers')

    config.add_route('no_device', 'no_device')
    config.add_route('no_team_server_device', 'no_team_server_device')

    config.add_route('json.rename_device', 'devices/rename_device')
    config.add_route('json.unlink_device', 'devices/unlink_device')
    config.add_route('json.erase_device', 'devices/erase_device')
