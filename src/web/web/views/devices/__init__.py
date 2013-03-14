def includeme(config):
    # The "/devices" string must be identical to the one in DeviceRegistrationEmailer.java.
    # TODO (WW) use protobuf to share constants between Python and Java code?
    config.add_route('my_devices', '/devices')
    config.add_route('no_device', '/no_device')
    config.add_route('user_devices', '/admin/devices')
    config.add_route('team_server_devices', '/admin/team_servers')
    config.add_route('json.get_devices', '/devices/get_devices')
    config.add_route('json.rename_device', '/devices/rename_device')
    config.add_route('json.unlink_device', '/devices/unlink_device')
    config.add_route('json.erase_device', '/devices/erase_device')
