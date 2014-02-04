from web.util import add_routes

# Use a set not list to speed up route queries by the clients of this data
# structure (including RedirectMiddleware and the forbidden view).
routes = {
    # Setup
    # N.B. setup's resolved route path is referred to by RedirectMiddleware
    'setup',
    'setup_authorized',
    'json_set_license',
    'json_setup_set_data_collection',
    'json_setup_hostname',
    'json_setup_email',
    'json_verify_smtp',
    'json_setup_certificate',
    'json_setup_identity',
    'json_verify_ldap',
    'json_upload_backup',
    'json_setup_finalize',
    'json_get_license_shasum_from_session',

    # Bootstrap
    'json_enqueue_bootstrap_task',
    'json_get_bootstrap_task_status',

    # Other maintenance routes
    'maintenance_login',
    'maintenance_login_submit',
    'backup_appliance',
    'logs',
    'logs_auto_download',
    'download_logs',
    'auditing',
    'json_set_auditing',
    # 'apps' is already taken by the access token management page
    'registered_apps',
    'register_app',
    'json_delete_app',
    # 'upgrade' is already taken by the organization settings page
    'upgrade_appliance',
    'download_backup_file',
    'status',
    'toggle_maintenance_mode',
    'maintenance_mode'
}


def includeme(config):
    add_routes(config, routes)
    config.add_route('maintenance_home', '/')