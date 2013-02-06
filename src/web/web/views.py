import logging
from pyramid.httpexceptions import HTTPFound
from pyramid.exceptions import NotFound
from pyramid.security import NO_PERMISSION_REQUIRED
from pyramid.view import view_config
from modules.login.views import logout

log = logging.getLogger("web")

# Global view configuration.
@view_config(
    route_name = 'homepage',
    permission = 'user'
)
def homepage(request):
    # Redirects to the accept page.
    return HTTPFound(request.route_url('accept'))

# Exception handlers.

# Server errors.
@view_config(
    context=Exception,
    permission=NO_PERMISSION_REQUIRED,
    renderer = 'error.mako'
)
def mako_exception(context, request):
    log.error("Exception caught:", exc_info=context)
    request.response_status = 500

    # Log out when we encounter an exception so that if the session is broken
    # recovery is still possible.
    logout(request)
    return {}

# Not found view.
@view_config(
    context=NotFound,
    permission=NO_PERMISSION_REQUIRED,
    renderer = 'not_found.mako'
)
def not_found_view(request):
    request.response_status = 404
    return {'navigation':[]}
