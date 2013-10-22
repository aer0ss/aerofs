# Important: if you add a route here, you need to whitelist it in the
# RedirectMiddleware class (see ../../__init__.py).
def includeme(config):
    config.add_route('setup', '/setup')
    config.add_route('setup_redirect', '/setup_redirect')

    # Intermediate configuration steps.
    config.add_route('json_setup_hostname', 'json_setup_hostname')
    config.add_route('json_verify_smtp', 'json_verify_smtp')
    config.add_route('json_setup_email', 'json_setup_email')
    config.add_route('json_setup_certificate', 'json_setup_certificate')

    # Final configuration steps.
    config.add_route('json_setup_apply', 'json_setup_apply')
    config.add_route('json_setup_poll', 'json_setup_poll')
    config.add_route('json_setup_finalize', 'json_setup_finalize')
