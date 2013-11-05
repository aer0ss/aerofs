
routes = {
    'setup',
    'setup_authorized',

    # Intermediate configuration steps.
    'json_set_license',
    'json_setup_hostname',
    'json_setup_email',
    'json_verify_smtp',
    'json_setup_certificate',
    'json_setup_identity',
    'json_verify_ldap',

    # Final configuration steps.
    'json_setup_apply',
    'json_setup_poll',
    'json_setup_finalize'
}

def includeme(config):
    for item in routes:
        config.add_route(item, item)