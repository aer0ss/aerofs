def includeme(config):
    # The URL string must be consistent with BaseParam.java
    config.add_route('request_password_reset', 'request_password_reset')
    config.add_route('password_reset', 'password_reset')
