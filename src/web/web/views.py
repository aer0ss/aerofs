from pyramid.httpexceptions import HTTPFound
from pyramid.security import NO_PERMISSION_REQUIRED
from pyramid.view import view_config

from aerofs_web.helper_functions import flash_error

# Global view configuration

# basic homepage view with links to pages
@view_config(
    route_name = 'homepage',
    renderer = 'home.mako',
    permission = 'user'
)
def homepage(request):
    return {}


# 404 not found
@view_config(
    context='pyramid.exceptions.NotFound',
    permission=NO_PERMISSION_REQUIRED,
    renderer = 'not_found.mako'
)
def not_found_view(request):
    request.response_status = '404 Not Found'
    return {'navigation':[]}

@view_config(
    context='pyramid.exceptions.Forbidden',
    renderer = 'login.mako',
    permission=NO_PERMISSION_REQUIRED
)
def not_authorized_view(request):
    _ = request.translate

    request.response_status = '401 Unauthorized'
    return {
        'next': request.matched_route.path,
        'not_authorized': _("You are not authorized to view this page. Please log in to continue."),
        'did_fail': False
    }
