def includeme(config):
    config.add_route('json.set_auth_level', 'set_auth_level')

    # The "/users" string must be consistent with BaseParam.java.
    config.add_route('org_users', '/users')
    config.add_route('json.list_org_users', 'users/list')
    config.add_route('json.invite_user', 'users/invite')
    config.add_route('json.delete_org_invitation', 'users/invitations/delete')
    config.add_route('json.remove_user', 'users/remove')
    config.add_route('json.deactivate_user', 'users/deactivate')
