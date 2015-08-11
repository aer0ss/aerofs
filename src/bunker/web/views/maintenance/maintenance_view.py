import logging

from pyramid.httpexceptions import HTTPFound
from pyramid.security import NO_PERMISSION_REQUIRED, authenticated_userid, remember
from pyramid.view import view_config

from maintenance_util import is_maintenance_mode, is_configuration_initialized
from web.license import set_license_file_and_attach_shasum_to_session
from web.login_util import URL_PARAM_NEXT, get_next_url, \
    redirect_to_next_page
from web.util import flash_error
from web.views.maintenance.maintenance_util import get_conf
import requests

log = logging.getLogger(__name__)

URL_PARAM_LICENSE = 'license'

_DEFAULT_NEXT = 'status'


@view_config(
    route_name='login',
    permission=NO_PERMISSION_REQUIRED,
    renderer='login.mako'
)
def login(request):
    return {
        'is_initialized': is_configuration_initialized(request.registry.settings),
        'url_param_license': URL_PARAM_LICENSE,
        'url_param_next': URL_PARAM_NEXT,
        'next': get_next_url(request, _DEFAULT_NEXT)
    }


@view_config(
    route_name='login_submit',
    permission=NO_PERMISSION_REQUIRED,
    request_method='POST'
)
def login_submit(request):
    log.info("attempt to login with license. auth'ed userid: {}"
        .format(authenticated_userid(request)))

    # TODO (WW) share code with setup_view.py:json_set_license()?

    license_bytes = ''
    while True:
        buf = request.POST[URL_PARAM_LICENSE].file.read(4096)
        if not buf: break
        license_bytes += buf

    ########
    # N.B. & TODO (WW)
    #
    # We don't restart services here. That means if the admin logs in with a new
    # license file, it will not take effect until the next time the system
    # restarts or reconfigures. It is okay for now but we need to revisit it
    # later.

    if not set_license_file_and_attach_shasum_to_session(request, license_bytes):
        flash_error(request, "The license is incorrect.")
        return HTTPFound(location=request.route_path('login'))

    headers = remember(request, 'fakeuser')
    return redirect_to_next_page(request, headers, False, False, _DEFAULT_NEXT)


@view_config(
    route_name='toggle_maintenance_mode',
    permission='maintain',
    renderer='toggle_maintenance_mode.mako'
)
def toggle_maintenance_mode(request):
    return {
        'is_maintenance_mode': is_maintenance_mode(request.registry.settings)
    }


@view_config(
    route_name='maintenance_home',
    # The target route of the redirect manages permissions
    permission=NO_PERMISSION_REQUIRED,
)
def maintenance_home(request):
    """
    The default page of the maintenance site http://host.name:8484
    """
    if not is_configuration_initialized(request.registry.settings):
        redirect = 'setup'
    elif is_maintenance_mode(request.registry.settings):
        # The status page is inaccessible during maintenance
        redirect = 'toggle_maintenance_mode'
    else:
        redirect = 'status'
    return HTTPFound(location=request.route_path(redirect))


@view_config(
    route_name='redirect',
    permission=NO_PERMISSION_REQUIRED,
)
def maintenance_redirect(request):
    """
    Nginx redirects the user to this route when they access the appliance's main URL http{,s}://host.name.
    Depending on the system's state, this route redirects to an appropriate page.
    """
    if not is_configuration_initialized(request.registry.settings):
        redirect = 'setup'
    else:
        r = requests.get('http://config.service:5434/is_license_valid')
        r.raise_for_status()
        redirect = 'license_expired' if r.text.strip() == '0' else 'maintenance_home'

    return HTTPFound(location=request.route_path(redirect))


@view_config(
    route_name='license_expired',
    permission=NO_PERMISSION_REQUIRED,
    renderer='license_expired.mako'
)
def license_expired(request):
    # Return status 503 Service Unavailable
    request.response.status = 503
    return {
        'support_email': get_conf(request)['base.www.support_email_address']
    }
