import logging
from aerofs_common.exception import ExceptionReply
from aerofs_sp.gen.common_pb2 import PBException
from pyramid.exceptions import NotFound
from pyramid.security import NO_PERMISSION_REQUIRED
from pyramid.view import view_config, forbidden_view_config
from web.views.error.error_view_util import force_login

log = logging.getLogger(__name__)

######
# N.B. both HTML and AJAX requests use these mthods to handle errors.
######


@view_config(
    context=NotFound,
    permission=NO_PERMISSION_REQUIRED,
    renderer='404.mako'
)
def not_found_view(request):
    request.response_status = 404
    return {}


@forbidden_view_config(
    renderer="403.mako"
)
def forbidden_view(request):
    log.error("forbidden view for " + request.path)
    return force_login(request, 'login')


@view_config(
    context=ExceptionReply,
    permission=NO_PERMISSION_REQUIRED,
    renderer='500.mako'
)
def protobuf_exception_view(context, request):
    # SP throws NOT_AUTHENTICATED if the user is not logged in.
    if context.get_type() == PBException.NOT_AUTHENTICATED:
        log.warn("Not authenticated")
        return force_login(request, 'login')
    else:
        log.error("default handling for ExceptionReply:", exc_info=context)
        request.response_status = 500
        return {}


@view_config(
    context=Exception,
    permission=NO_PERMISSION_REQUIRED,
    renderer='500.mako'
)
def exception_view(context, request):
    log.error("default handling for general exception:", exc_info=context)

    request.response_status = 500
    return {}

