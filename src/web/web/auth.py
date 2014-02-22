# Authentication / authorization related utility functions.
#
# N.B. an attacker could forge the auth level field in the cookie, but because
# login_view.py:get_principals() calls _get_auth_level() every time
# a request is made, it is not a problem.
#
import logging
from pyramid.security import authenticated_userid
from aerofs_sp.gen.sp_pb2 import ADMIN
from util import get_rpc_stub

GROUP_ID_ADMINS = 'group:admins'
GROUP_ID_USERS = 'group:users'

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
    except Exception:
        # SP may throw ExNotAuthenticated because the web session and SP's
        # session are maintained separately. In which case, we can safely
        # assume the web user has no permissions to access any resource. If
        # additional permission is required, the user will be redirected to
        # login via error_view.forbidden_view().
        log.warn('invalid SP session')
        return []

    request.session[_SESSION_KEY_IS_ADMIN] = level == ADMIN
    return [GROUP_ID_ADMINS if level == ADMIN else GROUP_ID_USERS]
