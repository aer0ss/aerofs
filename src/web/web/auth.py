# Authentication / authorization related utility functions.
#
# N.B. an attacker could forge the auth level field in the cookie, but because
# login_view.py:get_principals() calls _get_auth_level() every time
# a request is made, it is not a problem.
#
# Related documentation: docs/design/site_setup_auth.md
#
import logging
from pyramid.security import authenticated_userid
from aerofs_sp.gen.sp_pb2 import ADMIN
from util import get_rpc_stub
from web.license import is_license_shasum_set, is_license_shasum_valid

# A fake user ID for the system to tell if the user has logged in with SP. SP
# login system must prevent users from signing in using this ID.
NON_SP_USER_ID = 'fakeuser'

GROUP_ID_MAINTAINERS = 'group:maintainers'
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

    Note that if calls to the server throw for any reason, e.g. config server is
    down or SP is under maintenance, do not add corresponding principals. This
    potentially triggers the forbidden view which redirects the user to the
    login page.

    See docs/design/site_setup_auth.md for more information.

    @return the list of principals
    """
    principals = []

    if is_license_shasum_set(request):
        try:
            if is_license_shasum_valid(request):
                principals.append(GROUP_ID_MAINTAINERS)
        except Exception as e:
            print "is_license_shasum_valid() for {}:".format(authed_userid), e

    if authed_userid != NON_SP_USER_ID:
        try:
            level = get_rpc_stub(request).get_authorization_level().level
            group = GROUP_ID_ADMINS if level == ADMIN else GROUP_ID_USERS
            principals.append(group)
        except Exception as e:
            print "sp.get_auth_level() for {}:".format(authed_userid), e

    # Cache result for is_admin() everytime this method is called.
    request.session[_SESSION_KEY_IS_ADMIN] = GROUP_ID_ADMINS in principals

    log.debug("{}'s principals: {}".format(authed_userid, principals))
    return principals
