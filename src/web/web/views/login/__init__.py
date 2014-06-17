def includeme(config):
    # N.B. the URL path "login" is also used in aerofs.js. Keep them consistent!
    config.add_route('login_openid_begin', 'login_openid_begin')
    config.add_route('login_openid_complete', 'login_openid_complete')
    config.add_route('login', 'login')
    config.add_route('logout', 'logout')
    config.add_route('login_for_tests.json', 'login_for_tests.json')
    config.add_route('login_second_factor', 'login_second_factor')
    config.add_route('login_backup_code', 'login_backup_code')
