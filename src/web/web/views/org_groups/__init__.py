def includeme(config):
    config.add_route('org_groups', '/groups')
    config.add_route('json.list_org_groups', 'groups/list')
    config.add_route('json.add_org_group', 'groups/add')
    config.add_route('json.remove_org_group', 'groups/delete')