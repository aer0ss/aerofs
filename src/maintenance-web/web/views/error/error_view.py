import logging
from pyramid.exceptions import NotFound
from pyramid.httpexceptions import HTTPFound
from pyramid.security import NO_PERMISSION_REQUIRED
from pyramid.view import view_config

log = logging.getLogger(__name__)


@view_config(
    context=NotFound,
    permission=NO_PERMISSION_REQUIRED,
    renderer='404.mako'
)
def not_found_view(request):
    redirect = request.route_path('dashboard_home')
    return HTTPFound(location=redirect)
