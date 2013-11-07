from web.util import add_routes

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
    'json_upload_backup',

    # Final configuration steps.
    'json_setup_finalize'
}

def includeme(config):
    add_routes(config, routes)
