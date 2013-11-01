# Authentication / authorization related utility functions
#
# We cache auth level and other authorization data in the session to avoid
# frequent queries to SP.
#
# N.B. an attacker could forge the group field in the cookie, but because
# login_view.py:get_group() calls invalidate_auth_level_cache() everytime a
# request is made, it is not a problem.
#
from aerofs_sp.gen.sp_pb2 import ADMIN
from web.util import get_rpc_stub

_USER_ID_KEY = 'username'
_AUTH_LEVEL_KEY = 'auth_level'

def is_logged_in(request):
    return _USER_ID_KEY in request.session

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

def _refresh_auth_level_cache(request):
    """
    Get the auth level from SP server and refresh the cache.

    @see get_auth_level()
    """
    sp = get_rpc_stub(request)
    level = int(sp.get_authorization_level().level)
    request.session[_AUTH_LEVEL_KEY] = level

def get_group(userid, request):
    # Always fetch the latest authorization level from SP
    _refresh_auth_level_cache(request)
    return [get_auth_level(request)]