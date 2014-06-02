def includeme(config):
    config.add_route('org_shared_folders', '/org/shared_folders')
    config.add_route('user_shared_folders', '/users/shared_folders')
    # N.B. The "shared_folders" URL string is refered to in
    # SharedFolderNotificationEmailer.java
    config.add_route('my_shared_folders', '/shared_folders')

    config.add_route('json.get_my_shared_folders', '/get_my_shared_folders')
    config.add_route('json.get_user_shared_folders', '/get_user_shared_folders')
    config.add_route('json.get_org_shared_folders', '/get_org_shared_folders')
    config.add_route('json.add_shared_folder_perm', '/add_shared_folder_perm')
    config.add_route('json.leave_shared_folder', '/leave_shared_folder')
    config.add_route('json.destroy_shared_folder', '/destroy_shared_folder')
    config.add_route('json.set_shared_folder_perm', '/set_shared_folders_perm')
    config.add_route('json.delete_shared_folder_perm', '/delete_shared_folders_perm')
