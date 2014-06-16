import logging

from pyramid.httpexceptions import HTTPFound
from pyramid.security import remember

from aerofs_sp.gen.sp_pb2 import SPServiceRpcStub
from aerofs_sp.connection import SyncConnectionService

# N.B. This parameter is also used in aerofs.js. Remember to update this and all
# other references to this parameter when modifying handling of the next parameter.
URL_PARAM_NEXT = 'next'

log = logging.getLogger(__name__)

def get_next_url(request, default_route):
    """
    Return the value of the 'next' parameter in the request. Return the
    dashboard home URL if the parameter is absent. It never returns None
    """
    next_url = request.params.get(URL_PARAM_NEXT)
    return next_url if next_url else request.route_path(default_route)


def resolve_next_url(request, default_route):
    """
    Return the value of the 'next' parameter in the request. Return
    default_route if the next param is not set. Always prefix with the host URL
    to prevent attackers to insert arbitrary URLs in the parameter, e.g.:
    aerofs.com/login?next=http%3A%2F%2Fcnn.com.
    """
    next_url = get_next_url(request, default_route)

    # If _get_next_url()'s return value doesn't include a leading slash, add it.
    # This is to prevent the next_url being things like .cnn.com and @cnn.com
    if next_url[:1] != '/': next_url = '/' + next_url

    return request.host_url + next_url


def redirect_to_next_page(request, headers, default_route):
    """
    Resolve the next URL from the request and redirect to the URL.

    Note: this logic is very similar to openid.py:login_openid_complete().
    Remmember to update that function when updating this one.

    @param headers: the headers remember() returns
    @return: an HTTPFound object that the caller should return to the system.
    """
    redirect = resolve_next_url(request, default_route)
    log.debug("login redirect to {}".format(redirect))
    return HTTPFound(location=redirect, headers=headers)

def log_in_user(request, login_func, stay_signed_in=False, **kw_args):
    """
    Attempts to log in to SP using login_func(**kw_args)

    login_func must be a callable that takes the request context, an SP RPC
    stub, and any additional keyword args passed to this function, like
    userid/cred or session_nonce.  It should perform the appropriate login call
    and return a tuple of (authenticated_userid, need_second_factor).

    Returns a set of headers to create a session for the user.
    Could potentially throw any protobuf exception that SP throws.
    """

    # ignore any session data that may be saved
    settings = request.registry.settings
    con = SyncConnectionService(settings['base.sp.url'], settings['sp.version'])
    sp = SPServiceRpcStub(con)

    # Log in using the login_func provided
    second_factor_needed = False
    userid, second_factor_needed = login_func(request, sp, **kw_args)

    if stay_signed_in:
        log.debug("Extending session")
        sp.extend_session()

    # Save the cookies in the session, for reuse in future requests
    request.session['sp_cookies'] = con._session.cookies
    request.session['team_id'] = sp.get_organization_id().org_id

    return remember(request, userid), second_factor_needed
