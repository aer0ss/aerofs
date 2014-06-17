import logging

from pyramid.httpexceptions import HTTPFound, HTTPForbidden
from aerofs_common.exception import ExceptionReply

from web.login_util import URL_PARAM_NEXT

log = logging.getLogger(__name__)


def force_login(request):

    log.warn("request to login (xhr={})".format(request.is_xhr))

    # Return an plain 403 page if it's an AJAX request. Also look at how
    # aerofs.js:showErrorMessageFromResponse handles 403 errors.
    #
    # TODO (WW) Ideally we should return the login page with 403 as the code,
    # regardless whether it's an XHR. But how?
    #
    # TODO (WW) the browser should cache forbidden errors and automatically
    # redirect to login page. Also see datatables.js:forceLogout().
    if request.is_xhr: return HTTPForbidden()

    # Most auth failures should get sent to login, but if you have already
    # logged in, you'll get a SECOND_FACTOR_REQUIRED exception, and you should
    # skip /login and go straight to /login_second_factor.
    login_route = 'login'
    if hasattr(request, 'hack_sp_exception'):
        if isinstance(request.hack_sp_exception, ExceptionReply):
            if request.hack_sp_exception.get_type_name() == "SECOND_FACTOR_REQUIRED":
                login_route = 'login_second_factor'

    # path_qs: the request path without host but with query string
    #
    # N.B.: never include host in the string, as login_view:resolve_next_url()
    # always prefix the host URL to this string before redirection. See that
    # method for detail.
    next_url = request.path_qs

    # Test against '/' so that we don't get annoying next=%2F in the url when we
    # click on the home button.
    if next_url and next_url != '/':
        loc = request.route_url(login_route, _query={URL_PARAM_NEXT: next_url})
    else:
        loc = request.route_url(login_route)

    return HTTPFound(location=loc)
