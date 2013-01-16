import logging
from pyramid.httpexceptions import HTTPFound
from pyramid.exceptions import NotFound
from pyramid.security import NO_PERMISSION_REQUIRED
from pyramid.view import view_config
from aerofs_sp.gen.sp_pb2 import USER

log = logging.getLogger(__name__)

# Global view configuration.

# Redirects to either the user or the admin homepage.
@view_config(
    route_name = 'homepage',
    permission = 'user'
)
def homepage(request):
    if request.session['group'] == USER:
        return HTTPFound(request.route_url('user_home'))
    else:
        return HTTPFound(request.route_url('admin_home'))

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
