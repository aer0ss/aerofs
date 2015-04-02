from web.util import add_routes


def includeme(config):
    add_routes(config, [
        # The "signup" path must be consistent with RequestToSignUpEmailer.java.
        'signup',
        'signup_code_not_found',
        'json.request_to_signup',
        'json.signup',
        # The "create_first_user" path must be consistent with apply_page.mako
        # in bunker.
        'create_first_user'
    ])
    config.add_route('json.business_inquiry', 'business_inquiry')
