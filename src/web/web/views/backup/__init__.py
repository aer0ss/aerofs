from web import util

def includeme(config):
    util.add_routes(config, [
        'backup_appliance',
        # 'upgrade' is already taken by the team settings page
        'upgrade_appliance',
        'download_backup_file'
    ])
