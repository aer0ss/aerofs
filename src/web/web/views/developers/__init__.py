def includeme(config):
    # Routes for developers pages
    config.add_route('developers_signup', 'developers/signup')
    config.add_route('json.developers_signup', 'developers/json.signup')