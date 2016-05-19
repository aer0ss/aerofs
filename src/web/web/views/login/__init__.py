def includeme(config):
    # N.B. the URL path "login" is also used in aerofs.js. Keep them consistent!
    config.add_route('login_ext_auth_begin', 'login_ext_auth_begin')
    config.add_route('login_ext_auth_complete', 'login_ext_auth_complete')
    config.add_route('login', 'login')
    config.add_route('logout', 'logout')
    config.add_route('login_for_tests.json', 'login_for_tests.json')
    config.add_route('login_second_factor', 'login_second_factor')
    config.add_route('login_backup_code', 'login_backup_code')
