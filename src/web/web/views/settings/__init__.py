def includeme(config):
    config.add_route('access_tokens', 'apps')
    config.add_route('app_authorization', 'authorize')

    # Settings.
    config.add_route('settings', 'settings')

    config.add_route('json_create_user_settings_access_token','json_create_user_settings_access_token')
    config.add_route('json_delete_user_settings_access_token','json_delete_user_settings_access_token')

    config.add_route('json_send_password_reset_email','json_send_password_reset_email')
    config.add_route('json_set_full_name','json_set_full_name')
    config.add_route('json_delete_user','json_delete_user')

    # 2FA.
    config.add_route('two_factor_settings', 'settings/two_factor_authentication')
    config.add_route('two_factor_intro', 'settings/two_factor_authentication/intro')
    config.add_route('two_factor_setup', 'settings/two_factor_authentication/setup')
    config.add_route('two_factor_disable', 'settings/two_factor_authentication/disable')
    config.add_route('two_factor_download_backup_codes', 'settings/two_factor_authentication/backup_codes')

    # Apps.
    config.add_route('json_delete_access_token','json_delete_access_token')
