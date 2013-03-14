import logging
from pyramid.httpexceptions import HTTPFound, HTTPForbidden, HTTPBadRequest
from pyramid.security import remember, authenticated_userid, forget, NO_PERMISSION_REQUIRED
from pyramid.view import view_config, forbidden_view_config

"""
TODO:
Basic unit tests for each view so that we can catch stupid errors such as missing import statements.
"""

log = logging.getLogger("web")

@view_config(
    route_name='install',
    permission='user',
)
def install(request):
    # eventually we will have our own install page
    raise HTTPFound('https://www.aerofs.com/download')

@view_config(
    route_name='install_team_server',
    renderer='install_team_server.mako',
    permission='admin',
)
def payment_signup_done(request):
    return {};
