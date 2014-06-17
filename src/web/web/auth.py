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
    except Exception as e:
        # SP may throw an exception here.  We return no additional principals.
        # The pyramid view may be okay with anonymous viewing, or it may require
        # a valid user session.  If the latter, it might want to know what kind
        # of failure this was, so it can give a useful redirect to either /login
        # or /login_second_factor or whatnot.

        # Since there appears to be no way to cleanly pass this along for just
        # the duration of the request, we do a messy thing and stick the
        # exception in the request object so we can access it later if needed.
        log.warn('invalid SP session')
        request.hack_sp_exception = e
        return []

    request.session[_SESSION_KEY_IS_ADMIN] = level == ADMIN
    return [GROUP_ID_ADMINS if level == ADMIN else GROUP_ID_USERS]
