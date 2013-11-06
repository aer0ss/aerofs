def includeme(config):
    config.add_route('backup', 'backup')
    config.add_route('update', 'update')
    config.add_route('json_kickoff_backup', 'json_kickoff_backup')
    config.add_route('json_kickoff_maintenance_exit', 'json_kickoff_maintenance_exit')
    config.add_route('json_poll_bootstrap', 'json_poll_bootstrap')
    config.add_route('download_backup_file', 'download_backup_file')
