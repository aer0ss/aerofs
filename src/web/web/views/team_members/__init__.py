def includeme(config):
    # The "'admin/team_members" string must be consistent with BaseParam.java.
    # TODO (WW) use protobuf to share constants between Python and Java code?
    config.add_route('json.set_level', 'admin/set_level')

    config.add_route('team_members_old', 'admin/users') # for compatibility with older clients. Remove after July 2013, here and in team_member_view.py
    config.add_route('team_members', 'admin/team_members')
    config.add_route('json.list_team_members', 'admin/team_members/list')
    config.add_route('json.invite_user', 'admin/team_members/invite')
    config.add_route('json.delete_team_invitation', 'admin/team_members/delete_invitation')
    config.add_route('json.remove_from_team', 'admin/team_members/remove')
