import logging

from pyramid.security import NO_PERMISSION_REQUIRED
from pyramid.view import view_config

from aerofs_web.helper_functions import flash_error

log = logging.getLogger(__name__)


# Global view configuration

# basic homepage view with links to pages
@view_config(
    route_name = 'homepage',
    renderer = 'home.mako',
    permission = 'user'
)
def homepage(request):
    return {}


# Exception handlers

# Server errors
@view_config(
    context=Exception,
    permission=NO_PERMISSION_REQUIRED,
    renderer = 'error.mako'
)
def mako_exception(context, request):
    log.error("Exception caught:", exc_info=context)

    request.response_status = 500
    return {}

# 404 not found
@view_config(
    context='pyramid.exceptions.NotFound',
    permission=NO_PERMISSION_REQUIRED,
    renderer = 'not_found.mako'
)
def not_found_view(request):
    request.response_status = 404
    return {'navigation':[]}

# 401 unauthorized
@view_config(
    context='pyramid.exceptions.Forbidden',
    renderer = 'login.mako',
    permission=NO_PERMISSION_REQUIRED
)
def not_authorized_view(request):
    _ = request.translate

    request.response_status = 401
    return {
        'next': request.matched_route.path,
        'not_authorized': _("You are not authorized to view this page. Please log in to continue."),
        'did_fail': False
    }
