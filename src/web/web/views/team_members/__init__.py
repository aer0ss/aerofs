def includeme(config):
    # The "/admin/users" string must be identical to the one in BaseParam.java.
    # TODO (WW) use protobuf to share constants between Python and Java code?
    config.add_route('team_members', '/admin/users')
    config.add_route('json.list_team_members', '/admin/list_team_members')
    config.add_route('json.set_level', '/admin/set_level')
    config.add_route('json.invite_user', '/admin/users/invite')
    config.add_route('json.delete_team_invitation', '/admin/users/delete_invitation')
