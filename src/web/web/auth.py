# Authentication / authorization related utility functions.
#
# N.B. an attacker could forge the auth level field in the cookie, but because
# login_view.py:get_principals() calls _get_auth_level() every time
# a request is made, it is not a problem.
#
import logging

from pyramid.security import authenticated_userid
from aerofs_common.exception import ExceptionReply
from aerofs_sp.gen.sp_pb2 import ADMIN

from util import get_rpc_stub

GROUP_ID_ADMINS = 'group:admins'
GROUP_ID_USERS = 'group:users'
GROUP_ID_TWO_FACTOR_LOGIN = 'group:two_factor_login'
GROUP_ID_TWO_FACTOR_SETUP = 'group:two_factor_setup'

log = logging.getLogger(__name__)


def is_authenticated(request):
    """
    This method returns True iff. remember() has been called
    """
    return authenticated_userid(request)

_SESSION_KEY_IS_ADMIN = 'admin'


def is_admin(request):
    """
    Return the authorization level cached in the session. We cache it
    to avoid frequent SP lookups triggered by is_admin(). This method may be
    called several times when serving a request.
    """
    if _SESSION_KEY_IS_ADMIN not in request.session:
        authed_userid = authenticated_userid(request)
        if authed_userid: get_principals(authed_userid, request)
        else: request.session[_SESSION_KEY_IS_ADMIN] = False
    return request.session[_SESSION_KEY_IS_ADMIN]


def get_principals(authed_userid, request):
    """
    This method is used as the callback for SessionAuthenticationPolicy().
    This is THE function that dictates authorization.
    """
    try:
        level = get_rpc_stub(request).get_authorization_level().level
    except Exception as e:
        # SP will return two particular types of exceptions for certain workflows:
        #
        #   - ExSecondFactorSetupRequired is thrown if the user has successfully
        #     provided their BASIC authentication (usually username/password),
        #     has not set up 2FA, but their organization has MANDATORY 2FA
        #     enforcement
        #   - ExSecondFactorRequired is thrown if the user has successfully
        #     provided their BASIC authentication, has enabled 2FA, but has
        #     not provided their second factor yet.
        #
        # In these cases, the user session contains a limited permission that
        # will allow them to resolve the situation - in the first case, by
        # setting up two-factor auth, and in the second case, by providing
        # their second factor or a backup code.
        #
        # The type of exception received may provide a useful piece of
        # information, like which page we should redirect the user to next if
        # they have insufficient permission to access the requested page.
        #
        # Since there appears to be no way to cleanly pass this exception along
        # for just the duration of the request, we do a messy thing and stick
        # the exception in the request object so we can access it later if
        # needed.
        log.warn('invalid SP session')
        request.hack_sp_exception = e
        if isinstance(request.hack_sp_exception, ExceptionReply):
            if request.hack_sp_exception.get_type_name() == "SECOND_FACTOR_SETUP_REQUIRED":
                # SP guarantees they have proven BASIC, and should be able to set up 2FA
                return [GROUP_ID_TWO_FACTOR_SETUP]
            if request.hack_sp_exception.get_type_name() == "SECOND_FACTOR_REQUIRED":
                # SP guarantees they have proven BASIC, and should be able to provide 2FA
                return [GROUP_ID_TWO_FACTOR_LOGIN]
        # If the exception wasn't one of these two special exceptions, assume no perms.
        return []

    request.session[_SESSION_KEY_IS_ADMIN] = level == ADMIN
    principals = [ GROUP_ID_TWO_FACTOR_LOGIN, GROUP_ID_TWO_FACTOR_SETUP, GROUP_ID_USERS ]
    if level == ADMIN:
        principals.append(GROUP_ID_ADMINS)
    return principals
