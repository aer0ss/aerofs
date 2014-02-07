import logging
from pyramid.httpexceptions import HTTPFound
from pyramid.security import NO_PERMISSION_REQUIRED, authenticated_userid, remember
from pyramid.view import view_config
from web.license import set_license_file_and_attach_shasum_to_session
from web.login_util import URL_PARAM_NEXT, get_next_url, \
    redirect_to_next_page
from web.util import flash_error, is_maintenance_mode, is_configuration_initialized_in_private_deployment
from web.views.maintenance.maintenance_util import get_conf

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
    return redirect_to_next_page(request, headers, _DEFAULT_NEXT)


@view_config(
    route_name='toggle_maintenance_mode',
    permission='maintain',
    renderer='toggle_maintenance_mode.mako'
)
def toggle_maintenance_mode(request):
    return {
        'is_maintenance_mode': is_maintenance_mode()
    }


@view_config(
    route_name='maintenance_home',
    ## The target routes of the redirect below manage permissions
    permission=NO_PERMISSION_REQUIRED,
)
def maintenance_home(request):
    # Redirect to the setup page if the system is not initialized
    if is_configuration_initialized_in_private_deployment():
        redirect = 'status'
    else:
        redirect = 'setup'
    return HTTPFound(location=request.route_path(redirect))


@view_config(
    route_name='maintenance_mode',
    permission=NO_PERMISSION_REQUIRED,
    renderer='maintenance_mode.mako'
)
def maintenance_mode(request):
    # Return status 503 Service Unavailable
    request.response.status = 503
    return {
        'support_email': get_conf(request)['base.www.support_email_address']
    }
