from web.util import add_routes

routes = [
    # Setup
    'setup',
    'setup_submit_data_collection_form',
    'json_setup_set_restored_from_backup',
    'json_setup_disable_data_collection',
    'json_setup_hostname',
    'json_setup_email',
    'json_verify_smtp',
    'json_setup_certificate',
    'json_setup_hpc',
    'json_verify_ldap',
    'json_upload_backup',
    'json_setup_finalize',

    'json-get-boot',
    'json-repackaging',
    'json-del-repackage-done',
    'json-set-configuration-completed',
    'json-backup',
    'json-has-disk-space',
    'json-needs-upgrade',
    'json-pull-images',
    'json-switch-appliance',
    'json-gc',
    'json-restore',
    'json-archive-container-logs',
    'json-status',

    # Bootstrap
    'json_enqueue_bootstrap_task',
    'json_get_bootstrap_task_status',

    # Other maintenance routes
    'login',
    'login_submit',
    'identity',
    'monitoring',
    'json_regenerate_monitoring_cred',
    'json_set_signup_restriction',
    'json_set_identity_options',
    'json_set_license',
    'preview_license',
    'backup_and_upgrade',
    'logs',
    'logs_auto_download',
    'download_logs',
    'collect_logs',
    'json_get_users',
    'json_collect_logs',
    'auditing',
    'sync_settings',
    'json_set_sync_settings',
    'json_set_auditing',
    'password_restriction',
    'json_set_password_restriction',
    'device_restriction',
    'json_set_device_authorization',
    'registered_apps',
    'register_app',
    'json_delete_app',
    'download_backup_file',
    'download_backup_script',
    'status',
    'toggle_maintenance_mode',
    'timekeeping',
    'json_set_mobile_device_management',
    'restore_settings',
    'json_upload_externalproperties',
    'autocomplete',
    'json_upload_additional_users',
    'customization',
    'email_integration',
    'session_management',
    'link_settings',
    'json_set_require_login',

    'maintenance_mode',
    'license_expired',
    'redirect'
]


def includeme(config):
    add_routes(config, routes)
    config.add_route('maintenance_home', '/')

    config.add_route('json-boot', 'json-boot/{target}')
