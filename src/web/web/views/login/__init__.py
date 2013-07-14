def includeme(config):
    # N.B. the URL path "login" is also used in aerofs.js. Keep them consistent!
    config.add_route('login', 'login')
    config.add_route('logout', 'logout')
