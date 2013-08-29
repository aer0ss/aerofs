import logging
from aerofs_sp.gen.common_pb2 import PBException
from pyramid import url
from pyramid.httpexceptions import HTTPFound
from pyramid.security import remember, forget, NO_PERMISSION_REQUIRED
from pyramid.view import view_config

from aerofs_sp.gen.sp_pb2 import SPServiceRpcStub
from aerofs_sp.connection import SyncConnectionService
from aerofs_sp.scrypt import scrypt

from web.util import *

# URL param keys.
URL_PARAM_FORM_SUBMITTED = 'form_submitted'
URL_PARAM_EMAIL = 'email'
URL_PARAM_PASSWORD = 'password'
URL_PARAM_REMEMBER_ME = 'remember_me'
URL_PARAM_NEXT = 'next' # N.B. the string "next" is also used in aerofs.js.


log = logging.getLogger(__name__)

def groupfinder(userid, request):
    return [request.session.get('group')]

def get_next_url(request):
    """
    Return a value for the 'next' parameter; which is dashboard_home
    if the next param is not set (or set to a login page).
    Never redirect to the login page (in any of its forms).
    Handles null or empty requested_url.
    """
    _next = request.params.get('next')

    if not _next:
        _next = request.url
        login_urls = [
            request.resource_url(request.context, ''),
            request.resource_url(request.context, 'login'),
            request.resource_url(request.context, 'login_credential'),
            request.resource_url(request.context, 'login_openid')
        ]

        if len(_next) == 0 or _next in login_urls:
            _next = request.route_path('dashboard_home')
    return _next


@view_config(
    route_name = 'login',
    permission=NO_PERMISSION_REQUIRED,
    renderer='login.mako'
)
def login(request):
    _next = get_next_url(request)
    settings = request.registry.settings
    if settings['deployment.mode'] != "prod" and settings.get('openid.service.enabled', "false").lower() == "true":
        _url = '{0}?{1}'.format(request.route_path('login_openid_view'), url.urlencode({'next' : _next}))
        return HTTPFound(_url, {'next' : _next})
    else:
        _url = '{0}?{1}'.format(request.route_path('login_credential'), url.urlencode({'next' : _next}))
        return HTTPFound(_url, {'next' : _next})


@view_config(
    route_name = 'logout',
    permission = 'user'
)
def logout_view(request):
    return HTTPFound(
        location=request.route_path('login'),
        headers=logout(request)
    )

def logout(request):
    sp = get_rpc_stub(request)
    try:
        sp.sign_out()
    except Exception:
        # Server errors don't matter when logging out.
        pass

    # Delete session information from client cookies.
    request.session.delete()

    return forget(request)
