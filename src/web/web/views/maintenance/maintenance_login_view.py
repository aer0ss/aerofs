import logging
from pyramid.httpexceptions import HTTPFound
from pyramid.security import NO_PERMISSION_REQUIRED, authenticated_userid
from pyramid.view import view_config
from web.license import verify_license_file
from web.login_util import URL_PARAM_NEXT, get_next_url, redirect_to_next_page, remember_license_based_login
from web.util import flash_error

log = logging.getLogger(__name__)

URL_PARAM_LICENSE = 'license'

_DEFAULT_NEXT = 'status'

@view_config(
    route_name='maintenance_login',
    permission=NO_PERMISSION_REQUIRED,
    renderer='maintenance_login.mako'
)
def maintenance_login(request):
    return {
        'url_param_license': URL_PARAM_LICENSE,
        'url_param_next': URL_PARAM_NEXT,
        'next': get_next_url(request, _DEFAULT_NEXT)
    }

@view_config(
    route_name='maintenance_login_submit',
    permission=NO_PERMISSION_REQUIRED,
    request_method='POST'
)
def maintenance_login_submit(request):
    log.info("attempt to login with license. auth'ed userid: {}"
        .format(authenticated_userid(request)))

    if not verify_license_file(request, request.POST[URL_PARAM_LICENSE].file):
        flash_error(request, "The license is incorrect.")
        return HTTPFound(location=request.route_path('maintenance_login'))

    headers = remember_license_based_login(request)
    return redirect_to_next_page(request, headers, _DEFAULT_NEXT)
