def includeme(config):
    config.add_route('accept', '/accept')
    config.add_route('json.get_invites', '/admin/accept/get'),
    config.add_route('json.accept_organization_invitation', '/admin/accept/accept_organization')
    config.add_route('json.accept_folder_invitation', '/admin/accept/accept_folder')
    config.add_route('json.ignore_organization_invitation', '/admin/accept/ignore_organization')
    config.add_route('json.ignore_folder_invitation', '/admin/accept/ignore_folder')
