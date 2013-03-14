def includeme(config):
    # The "/signup" string must be identical to the one in RequestToSignUpEmailer.java.
    # TODO (WW) use protobuf to share constants between Python and Java code?
    config.add_route('signup', '/signup')
    config.add_route('json.signup', '/json.signup')
