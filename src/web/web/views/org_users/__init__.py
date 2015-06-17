def includeme(config):
    config.add_route('json.set_auth_level', 'set_auth_level')

    # The "/users" string must be consistent with BaseParam.java
    config.add_route('org_users', '/users')
    config.add_route('json.list_org_users', 'users/list')
    config.add_route('json.search_org_users', 'users/search')
    config.add_route('json.list_org_users_and_groups', 'users_and_groups/list')
    config.add_route('json.list_org_invitees', 'users/invitees/list')
    config.add_route('json.invite_user', 'users/invite')
    config.add_route('json.delete_org_invitation', 'users/invitations/delete')
    config.add_route('json.deactivate_user', 'users/deactivate')
    config.add_route('json.set_publisher_status', 'users/set_publisher_status')
    config.add_route('json.disable_two_factor', 'users/disable_two_factor')
