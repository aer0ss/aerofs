def includeme(config):
    # N.B. the string "/login" is also used in aerofs.js. Keep them consistent!
    config.add_route('login', '/login')
    config.add_route('logout', '/logout')
