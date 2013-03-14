def includeme(config):
    config.add_route('organization_shared_folders', '/admin/shared_folders')
    config.add_route('user_shared_folders', '/admin/user_shared_folders')
    config.add_route('my_shared_folders', '/')

    config.add_route('json.get_organization_shared_folders', '/get_organization_shared_folders')
    config.add_route('json.get_user_shared_folders', '/get_user_shared_folders')
    config.add_route('json.add_shared_folder_perm', '/add_shared_folder_perm')
    config.add_route('json.set_shared_folder_perm', '/set_shared_folders_perm')
    config.add_route('json.delete_shared_folder_perm', '/delete_shared_folders_perm')
