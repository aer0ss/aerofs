from web.util import add_routes

routes = {
    'setup',
    'setup_authorized',

    # Intermediate configuration steps.
    'json_set_license',
    'json_setup_hostname',
    'json_setup_email',
    'json_verify_smtp',
    'json_setup_certificate',
    'json_setup_identity',
    'json_verify_ldap',
    'json_upload_backup',

    # Final configuration steps.
    'json_setup_finalize',

    'json_enqueue_bootstrap_task',
    'json_get_bootstrap_task_status',

    'backup_appliance',
    # 'upgrade' is already taken by the team settings page
    'upgrade_appliance',
    'download_backup_file',
    'status',
}

def includeme(config):
    add_routes(config, routes)
