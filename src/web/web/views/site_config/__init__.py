# Important: if you add a route here, you need to whitelist it in the
# RedirectMiddleware class (see ../../__init__.py).
def includeme(config):
    config.add_route('site_config', '/site_config')
    config.add_route('site_config_redirect', '/site_config_redirect')

    # Intermediate configuration steps.
    config.add_route('json_config_hostname', 'json_config_hostname')
    config.add_route('json_config_email', 'json_config_email')
    config.add_route('json_config_certificate', 'json_config_certificate')

    # Final configuration steps.
    config.add_route('json_config_apply', 'json_config_apply')
    config.add_route('json_config_poll', 'json_config_poll')
    config.add_route('json_config_finalize', 'json_config_finalize')
