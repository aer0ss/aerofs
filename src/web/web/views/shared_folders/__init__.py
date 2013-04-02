def includeme(config):
    config.add_route('team_shared_folders', '/admin/team_shared_folders')
    config.add_route('user_shared_folders', '/admin/user_shared_folders')
    config.add_route('my_shared_folders', '/shared_folders')

    config.add_route('json.get_my_shared_folders', '/get_my_shared_folders')
    config.add_route('json.get_user_shared_folders', '/get_user_shared_folders')
    config.add_route('json.get_team_shared_folders', '/get_team_shared_folders')
    config.add_route('json.add_shared_folder_perm', '/add_shared_folder_perm')
    config.add_route('json.set_shared_folder_perm', '/set_shared_folders_perm')
    config.add_route('json.delete_shared_folder_perm', '/delete_shared_folders_perm')
