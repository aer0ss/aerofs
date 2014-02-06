import logging

# N.B. This parameter is also used in aerofs.js. Remember to update this and all
# other references to this parameter when modifying handling of the next parameter.
from pyramid.httpexceptions import HTTPFound

URL_PARAM_NEXT = 'next'

DEFAULT_DASHBOARD_NEXT = 'dashboard_home'

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
