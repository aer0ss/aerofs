import logging
from aerofs_common.exception import ExceptionReply
from aerofs_sp.gen.common_pb2 import PBException
from pyramid.exceptions import NotFound
from pyramid.security import NO_PERMISSION_REQUIRED
from pyramid.view import view_config, forbidden_view_config
from pyramid.httpexceptions import HTTPFound, HTTPForbidden
from web.license import get_license_shasum_from_query,\
    get_license_shasum_from_session, verify_license_shasum_and_attach_to_session
from web.views import maintenance
from web.login_util import URL_PARAM_NEXT, remember_license_based_login

log = logging.getLogger(__name__)

######
# N.B. both HTML and AJAX requests use these mthods to handle errors.
######

@view_config(
    context=NotFound,
    permission=NO_PERMISSION_REQUIRED,
    renderer = '404.mako'
)
def not_found_view(request):
    request.response_status = 404
    return {}

@forbidden_view_config(
    renderer="403.mako"
)
def forbidden_view(request):
    log.error("forbidden view for " + request.path)

    response = _attempt_license_shasum_login(request)
    return response if response else _force_login(request)

def _attempt_license_shasum_login(request):
    """
    If a request is made with a license shasum in the query string, attempt
    to login with the shasum and retry the request. This is necessary for
    access to the appliance management API through command lines.

    Skip this step if the shasum stored in the session is equal to the shasum
    in the query. This is to prevent infinite loops if the user got permission
    denied after he logs in with the license.

    @return a response if login successful, None otherwise
    """
    shasum = get_license_shasum_from_query(request)
    if shasum == get_license_shasum_from_session(request):
        log.info('license shasum already set. skip license-based login')
    elif verify_license_shasum_and_attach_to_session(request, shasum):
        log.info('license shasum in query string verified. login w/ license')
        remember_license_based_login(request)
        return request.invoke_subrequest(request, use_tweens=True)

@view_config(
    context=ExceptionReply,
    permission=NO_PERMISSION_REQUIRED,
    renderer = '500.mako'
)
def protobuf_exception_view(context, request):
    # SP throws NOT_AUTHENTICATED if the user is not logged in.
    if context.get_type() == PBException.NOT_AUTHENTICATED:
        log.warn("Not authenticated")
        return _force_login(request)
    else:
        log.error("default handling for ExceptionReply:", exc_info=context)
        request.response_status = 500
        return {}

@view_config(
    context=Exception,
    permission=NO_PERMISSION_REQUIRED,
    renderer = '500.mako'
)
def exception_view(context, request):
    log.error("default handling for general exception:", exc_info=context)

    request.response_status = 500
    return {}

def _force_login(request):

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

    # path_qs: the request path without host but with query string
    #
    # N.B.: never include host in the string, as login_view:resolve_next_url()
    # always prefix the host URL to this string before redirection. See that
    # method for detail.
    next_url = request.path_qs

    login_route = _get_login_route(request)

    # Test against '/' so that we don't get annoying next=%2F in the url when we
    # click on the home button.
    if next_url and next_url != '/':
        loc = request.route_url(login_route, _query={URL_PARAM_NEXT: next_url})
    else:
        loc = request.route_url(login_route)

    return HTTPFound(location=loc)

def _get_login_route(request):
    # Ideally we should redirect to maintenance_login as long as the requested
    # view callable requires 'maintain' permission. However it's difficult to
    # retrieve the callable's permission info (pyramid introspector is the way
    # to go), so we redirect to maintenance_login as long as the requested route
    # is one of the maintenance pages.
    if request.matched_route and \
            request.matched_route.name in maintenance.routes:
        return 'maintenance_login'
    else:
        return 'login'
