import logging
from aerofs_common.exception import ExceptionReply
from aerofs_sp.gen.common_pb2 import PBException
from pyramid.exceptions import NotFound
from pyramid.security import NO_PERMISSION_REQUIRED, authenticated_userid
from pyramid.view import view_config, forbidden_view_config
from pyramid.httpexceptions import HTTPFound, HTTPForbidden

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
    log.error("default handling for forbidden request: " + request.path)

    # do not ask the user to login again if he is already logged in
    if authenticated_userid(request):
        request.response_status = 403
        return {}
    else:
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

    # So that we don't get annoying next=%2F in the url when we click on the
    # home button.
    # TODO (WW) include request parameters to the next URL
    next = request.path.strip()
    if next and next != '/':
        loc = request.route_url('login', _query={'next': next})
    else:
        loc = request.route_url('login')

    return HTTPFound(location=loc)
