def includeme(config):
    # The "devices" and "team_servers" strings must be consistent with
    # BaseParam.java.
    config.add_route('my_devices', 'devices')
    config.add_route('user_devices', 'users/devices')
    config.add_route('team_server_devices', 'team_servers')

    config.add_route('json.get_devices', 'devices/get_devices')
    config.add_route('json.rename_device', 'devices/rename_device')
    config.add_route('json.unlink_device', 'devices/unlink_device')
    config.add_route('json.erase_device', 'devices/erase_device')
    config.add_route('add_mobile_device', '/devices/add_mobile_device')
    config.add_route('get_mobile_access_code', '/devices/get_mobile_access_code')