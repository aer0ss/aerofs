"""
TODO:
Basic unit tests for each view so that we can catch stupid errors such as
missing import statements.
"""

import logging
from pyramid.security import NO_PERMISSION_REQUIRED
from pyramid.view import view_config

from aerofs_sp.gen.common_pb2 import PBException
from web.util import flash_error, get_rpc_stub, is_valid_email, is_valid_password, str2bool
from aerofs_common.exception import ExceptionReply

log = logging.getLogger(__name__)

# Define views.
@view_config(
    route_name='request_password_reset',
    permission=NO_PERMISSION_REQUIRED,
    renderer='request_password_reset.mako'
)
def request_password_reset(request):
    _ = request.translate

    success = False
    error = ''
    password_expired = False
    userid = ''

    if 'password_expired' in request.params:
        password_expired = str2bool(request.params['password_expired'])

    if 'userid' in request.params:
        userid = request.params['userid']

    # Only POST requests can modify state. See README.security.txt
    # TOOD (WW) separate POST and GET handling to different view callables
    if request.method == 'POST':
        login = request.params['login']
        if not is_valid_email(login):
            error = _("Error: Invalid email address")
        else:
            sp = get_rpc_stub(request)
            try:
                sp.send_password_reset_email(login)
                success = True
            except Exception as e:
                # do nothing.  We don't want to expose user information about whether or not
                # an email exists.
                log.warn('Error received in password reset: ' + str(e))

    if error:
        flash_error(request, error)

    return {
        'success': success,
        'error': error,
        'password_expired': password_expired,
        'userid': userid
    }

@view_config(
    route_name='password_reset',
    permission=NO_PERMISSION_REQUIRED,
    renderer='password_reset.mako'
)
def password_reset(request):
    token = request.params.get("token")
    user_id = request.params.get("user_id")
    password = request.params.get("password", "").encode("utf-8")

    error = None
    valid_password = True
    not_found = False
    success = False
    password_already_exist = False

    # if we don't have both a token and a user_id, redirect to the token request page
    if token is None or user_id is None:
        error = "Invalid parameters"

    # Only POST requests can modify state. See README.security.txt
    # TOOD (WW) separate POST and GET handling to different view callables
    if request.method == 'POST':
        (valid_password, error) = is_valid_password(request, password)
        if valid_password:
            sp = get_rpc_stub(request)

            try:
                sp.reset_password(token, password)
                success = True
            except ExceptionReply as e:
                if e.get_type() == PBException.NOT_FOUND:
                    not_found = True
                if e.get_type() == PBException.PASSWORD_ALREADY_EXIST:
                    password_already_exist = True
                error = str(e)

            except Exception as e:
                error = str(e)
                log.warn(error)
        else:
            log.warn("did I make it here 4")
            log.warn(error)
            flash_error(request, error)

    return {
        "token":token,
        "user_id":user_id,
        "not_found":not_found,
        "valid_password":valid_password,
        "error":error,
        "success":success,
        'password_already_exist': password_already_exist
    }
