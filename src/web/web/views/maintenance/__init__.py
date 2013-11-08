from web.util import add_routes

# Use a set not list to speed up route queries by the clients of this data
# structure (including RedirectMiddleware and the forbidden view).
routes = {
    # Setup
    'setup',
    'setup_authorized',
    'json_set_license',
    'json_setup_hostname',
    'json_setup_email',
    'json_verify_smtp',
    'json_setup_certificate',
    'json_setup_identity',
    'json_verify_ldap',
    'json_upload_backup',
    'json_setup_finalize',

    # Bootstrap
    'json_enqueue_bootstrap_task',
    'json_get_bootstrap_task_status',

    # Other maintenance routes
    'maintenance_login',
    'maintenance_login_submit',
    'backup_appliance',
    # 'upgrade' is already taken by the team settings page
    'upgrade_appliance',
    'download_backup_file',
    'status',

    # This is an alias of 'status', so that users can use the URL '<host>/manage'
    # to access maintenance tools directly.
    'manage'
}

def includeme(config):
    add_routes(config, routes)