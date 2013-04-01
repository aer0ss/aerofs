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
from web.views.login.login_view import *

log = logging.getLogger(__name__)

# N.B. the string 'c' is also used in RequestToSignUpEmailer.java
# TODO (WW) use protobuf to share constants between Python and Java code?
URL_PARAM_SIGNUP_CODE = 'c'
URL_PARAM_FIRST_NAME = 'first_name'
URL_PARAM_LAST_NAME = 'last_name'

@view_config(
    route_name='signup',
    renderer='signup.mako',
    permission=NO_PERMISSION_REQUIRED,
)
def signup(request):
    _ = request.translate

    code = request.params.get(URL_PARAM_SIGNUP_CODE)

    if not code: raise HTTPBadRequest()

    try:
        sp = get_rpc_stub(request)
        return {
            'url_param_signup_code': URL_PARAM_SIGNUP_CODE,
            'url_param_form_submitted': URL_PARAM_FORM_SUBMITTED,
            'url_param_email': URL_PARAM_EMAIL,
            'url_param_first_name': URL_PARAM_FIRST_NAME,
            'url_param_last_name': URL_PARAM_LAST_NAME,
            'url_param_password': URL_PARAM_PASSWORD,
            'url_param_remember_me': URL_PARAM_REMEMBER_ME,
            'url_param_next': URL_PARAM_NEXT,
            'email_address' : sp.resolve_sign_up_code(code).email_address,
            'code' : code,
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

    code = request.params[URL_PARAM_SIGNUP_CODE]
    email_address = request.params[URL_PARAM_EMAIL]
    first_name = request.params[URL_PARAM_FIRST_NAME]
    last_name = request.params[URL_PARAM_LAST_NAME]
    password = request.params[URL_PARAM_PASSWORD]

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
