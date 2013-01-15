import logging
from pyramid.httpexceptions import HTTPFound
from pyramid.exceptions import NotFound
from pyramid.security import NO_PERMISSION_REQUIRED
from pyramid.view import view_config
from pyramid.view import forbidden_view_config
from aerofs_sp.gen.sp_pb2 import USER

log = logging.getLogger(__name__)

# Global view configuration

# basic homepage view with links to pages
@view_config(
    route_name = 'homepage',
    renderer = 'admin_home.mako',
    permission = 'user'
)
def homepage(request):
    if request.session['group'] == USER:
        # direct non-admin users to the invitation page
        return HTTPFound(request.route_url('accept'))
    else:
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

# Not found view
@view_config(
    context=NotFound,
    permission=NO_PERMISSION_REQUIRED,
    renderer = 'not_found.mako'
)
def not_found_view(request):
    request.response_status = 404
    return {'navigation':[]}

# Forbidden view
@forbidden_view_config()
def forbidden_view(request):
    request.response_status = 403
    return {'navigation':[]}
