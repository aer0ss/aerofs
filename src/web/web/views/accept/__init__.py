def includeme(config):
    config.add_route('accept', '/accept')
    config.add_route('json.get_invites', '/accept/get'),
    config.add_route('json.accept_team_invitation', '/accept/accept_team')
    config.add_route('json.accept_folder_invitation', '/accept/accept_folder')
    config.add_route('json.ignore_team_invitation', '/accept/ignore_team')
    config.add_route('json.ignore_folder_invitation', '/accept/ignore_folder')
    config.add_route('accept_team_invitation_done', '/accept/accept_team_done')
