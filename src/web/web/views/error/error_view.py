import logging
from aerofs_common.exception import ExceptionReply
from aerofs_sp.gen.common_pb2 import PBException
from pyramid.exceptions import NotFound
from pyramid.security import NO_PERMISSION_REQUIRED, authenticated_userid
from pyramid.view import view_config, forbidden_view_config
from pyramid.httpexceptions import HTTPFound

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
        return _login(request)

@view_config(
    context=ExceptionReply,
    permission=NO_PERMISSION_REQUIRED,
    renderer = '500.mako'
)
def protobuf_exception_view(context, request):
    log.error("default handling for ExceptionReply:", exc_info=context)

    # SP throws NO_PERM if the user is not logged in.
    # TODO (WW) use a different type, i.e. NOT_AUTHENTICATED, since SP throws
    # NO_PERM for other reasons as well.
    if context.get_type() == PBException.NO_PERM:
        return _login(request)
    else:
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

def _login(request):
    log.warn("redirect to login page")

    # So that we don't get annoying next=%2F in the url when we click on the
    # home button.
    next = request.path.strip()
    if next and next != '/':
        loc = request.route_url('login', _query=(('next', next),))
    else:
        loc = request.route_url('login')

    # TODO (WW) If this is returned to an AJAX request, the caller will treat
    # the request as successful because of the status code of HTTPFound. How to
    # return an error code with the login page as the content?
    return HTTPFound(location=loc)
