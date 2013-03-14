def includeme(config):
    # The "/admin/users" string must be identical to the one in BaseParam.java.
    # TODO (WW) use protobuf to share constants between Python and Java code?
    config.add_route('admin_users', '/admin/users')
    config.add_route('admin_settings', '/admin/settings')
    config.add_route('json.get_users', '/admin/users/get')
    config.add_route('json.invite_user', '/admin/users/invite')
    config.add_route('json.delete_organization_invitation_for_user', '/admin/users/delete_invitation')
    config.add_route('json.set_authorization', '/admin/users/setauthlevel')
    config.add_route('json.user_lookup', '/admin/settings/user_lookup')
    config.add_route('json.get_admins', '/admin/settings/get_admins')
