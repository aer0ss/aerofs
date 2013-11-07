import logging
from aerofs_common.exception import ExceptionReply
from aerofs_sp.gen.common_pb2 import PBException
from pyramid.exceptions import NotFound
from pyramid.security import NO_PERMISSION_REQUIRED, has_permission
from pyramid.view import view_config, forbidden_view_config
from pyramid.httpexceptions import HTTPFound, HTTPForbidden, HTTPOk
from web.views.login.login_view import URL_PARAM_NEXT

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

    return _force_login(request)

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

    # Test against '/' so that we don't get annoying next=%2F in the url when we
    # click on the home button.
    if next_url and next_url != '/':
        loc = request.route_url('login', _query={URL_PARAM_NEXT: next_url})
    else:
        loc = request.route_url('login')

    return HTTPFound(location=loc)
