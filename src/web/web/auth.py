# Authentication / authorization related utility functions.
#
# We cache auth level and other authorization data in the session to avoid
# frequent queries to SP.
#
# N.B. an attacker could forge the auth level field in the cookie, but because
# login_view.py:get_principals() calls _refresh_auth_level_cache() every time
# a request is made, it is not a problem.
#
# Related documentation: docs/design/site_setup_auth.md
#
import logging
from aerofs_sp.gen.sp_pb2 import ADMIN
from web.license import is_license_shasum_valid
from web.util import get_rpc_stub
from web.views import setup

log = logging.getLogger(__name__)

_USER_ID_KEY = 'username'
_AUTH_LEVEL_KEY = 'auth_level'

def is_logged_in(request):
    """
    This method assumes either get_session_user() has been called or
    get_principals() causes the auth level to be set.

    TODO (WW) remove the check for _USER_ID_KEY? to do it, we need to make sure
    get_principals() is always called _before_ potential calls to is_logged_in().
    """
    return _USER_ID_KEY in request.session or _AUTH_LEVEL_KEY in request.session

def get_session_user(request):
    return request.session[_USER_ID_KEY]

def set_session_user(request, user):
    request.session[_USER_ID_KEY] = user

def get_auth_level(request):
    """
    Return the authorization level cached in the session. Call
    _refresh_auth_level_cache() if the value is not cached.

    @return: aerofs_sp.gen.sp_pb2.USER or ADMIN.
    """
    if _AUTH_LEVEL_KEY not in request.session:
        _refresh_auth_level_cache(request)
    return request.session[_AUTH_LEVEL_KEY]

def is_admin(request):
    return get_auth_level(request) == ADMIN

def get_principals(userid, request):
    """
    This method is used as the callback for AuthTktAuthenticationPolicy()
    """
    # Always get the latest authorization level from SP
    _refresh_auth_level_cache(request)
    return [get_auth_level(request)]

def _refresh_auth_level_cache(request):
    """
    Get the auth level from SP server and/or the license service and refresh the
    auth level cache. This is THE function that dictates authorization.

    Do not perform license-based auth for non-setup pages. This is because many
    dashboard pages require usernames, and users logged in with the license may
    not have provided a username.

    See docs/design/site_setup_auth.md for more information.
    """

    # If it's one of setup pages, ...
    # "[1:]" is to remove leading slashes.
    if request.current_route_path()[1:] in setup.routes:
        # Perform both license-based auth and sp auth
        log.info('license-based auth')
        if is_license_shasum_valid(request):
            level = ADMIN
        else:
            log.info('license-based auth failed. fall back to sp auth')
            level = _get_sp_auth_level(request)
    else:
        # Otherwise only do SP auth
        level = _get_sp_auth_level(request)

    request.session[_AUTH_LEVEL_KEY] = int(level)

def _get_sp_auth_level(request):
    return get_rpc_stub(request).get_authorization_level().level
