"""
TODO:
Basic unit tests for each view so that we can catch stupid errors such as
missing import statements.
"""
import logging
from pyramid.httpexceptions import HTTPBadRequest, HTTPOk
from pyramid.security import NO_PERMISSION_REQUIRED
from pyramid.view import view_config

from aerofs_common.exception import ExceptionReply
from aerofs_sp.scrypt import scrypt
import aerofs_sp.gen.common_pb2 as common

from web.util import *

log = logging.getLogger("web")

@view_config(
    route_name='signup',
    renderer='signup.mako',
    permission=NO_PERMISSION_REQUIRED,
)
def signup(request):
    _ = request.translate

    # the parameter key string must be identical to the one in
    # RequestToSignUpEmailer.java
    # TODO (WW) use protobuf to share constants between Python and Java code?
    code = request.params.get('c')

    # Set default to billing. See RequestToSignUpEmailer.java for rationale
    # the parameter key string must be identical to the one in
    # RequestToSignUpEmailer.java
    # TODO (WW) use protobuf to share constants between Python and Java code?
    next = request.params.get('next') or request.route_path('business_activate')

    if not code: raise HTTPBadRequest()

    try:
        sp = get_rpc_stub(request)
        return {
            'email_address' : sp.resolve_sign_up_code(code).email_address,
            'code' : code,
            'next' : next
        }
    except ExceptionReply:
        # I can use HTTPServerError. But the error will be commonly caused by
        # wrong invitation code. and I don't want people to think they
        # successfully find a server-side bug by trying arbitrary codes :)
        raise HTTPBadRequest()

@view_config(
    # Eric says: If we are returning a JSON document it would be more appropriate to
    # call it 'signup.json'. I know this is inconsistent with other view names..
    # those other view names are wrong also.
    # I also think we should specify the 'request_method' attribute here so that
    # it's clear that this is only for processing POST requests.
    route_name = 'json.signup',
    renderer = 'json',
    permission=NO_PERMISSION_REQUIRED
)
def json_signup(request):
    _ = request.translate

    code = request.params['code']
    email_address = request.params['emailAddress']
    first_name = request.params['firstName']
    last_name = request.params['lastName']
    password = request.params['password']

    (is_valid_password, invalid_message) = valid_password_test(request, password)
    if not is_valid_password:
        return { 'error': invalid_message }

    cred = scrypt(password, email_address)

    try:
        sp = get_rpc_stub(request)
        sp.sign_up_with_code(code, cred, first_name, last_name)
        return HTTPOk()
    except ExceptionReply as e:
        if e.get_type() == common.PBException.BAD_CREDENTIAL:
            msg = "The account already exists.<br>" \
                  "Please try again with the password you used to create the account.<br>" \
                  "If you forget the password," \
                  " <a target='_blank' href='" + request.route_path('request_password_reset') + \
                  " '>click here</a> to reset it."
        else:
            msg = get_error(e)
        return { 'error' : msg }
