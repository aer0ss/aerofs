import logging

from pyramid import url
from pyramid.httpexceptions import HTTPFound
from pyramid.security import remember, forget, NO_PERMISSION_REQUIRED
from pyramid.view import view_config
from aerofs_common.exception import ExceptionReply

from aerofs_sp.gen.common_pb2 import PBException
from aerofs_sp.gen.sp_pb2 import SPServiceRpcStub
from aerofs_sp.connection import SyncConnectionService
import requests
from web.util import flash_error, get_rpc_stub, is_private_deployment

from web.login_util import get_next_url, URL_PARAM_NEXT, redirect_to_next_page

DEFAULT_DASHBOARD_NEXT = 'dashboard_home'

URL_PARAM_EMAIL = 'email'
URL_PARAM_PASSWORD = 'password'
URL_PARAM_REMEMBER_ME = 'remember_me'

log = logging.getLogger(__name__)

# A boolean indicating whether the system has created one or more users.
_has_users_cache = False


def _is_openid_enabled(request):
    """
    True if the local deployment allows OpenID authentication
    """
    return is_private_deployment(request.registry.settings) \
        and request.registry.settings.get('lib.authenticator', 'local_credential').lower() == 'openid'


def _do_login(request):
    """
    Log in the user. Return HTTPFound if login is successful; otherwise call
    flash_error() to insert error messages and then return None.
    """
    _ = request.translate

    # Remember to normalize the email address.
    login = request.params[URL_PARAM_EMAIL]
    password = request.params[URL_PARAM_PASSWORD].encode("utf-8")
    stay_signed_in = URL_PARAM_REMEMBER_ME in request.params
    try:
        try:
            headers = _log_in_user(request, login, password, stay_signed_in)
            return redirect_to_next_page(request, headers, DEFAULT_DASHBOARD_NEXT)
        except ExceptionReply as e:
            if e.get_type() == PBException.BAD_CREDENTIAL:
                log.warn(login + " attempts to login w/ bad password")
                flash_error(request, _("Email or password is incorrect."))
            elif e.get_type() == PBException.EMPTY_EMAIL_ADDRESS:
                flash_error(request, _("The email address cannot be empty."))
            else:
                raise e
    except Exception as e:
        log.error("error during logging in:", exc_info=e)
        support_email = request.registry.settings.get(
                'base.www.support_email_address', 'support@aerofs.com')
        flash_error(request, _("An error occurred processing your request." +
                " Please try again later. Contact " + support_email + " if the" +
                " problem persists."))


@view_config(
    route_name='login',
    permission=NO_PERMISSION_REQUIRED,
    renderer='login.mako'
)
def login_view(request):
    _ = request.translate
    settings = request.registry.settings

    if request.method == "POST":
        ret = _do_login(request)
        if ret: return ret

    openid_enabled = _is_openid_enabled(request)

    if not openid_enabled and not _has_users(settings):
        log.info('no users yet. ask to create the first user')
        return HTTPFound(location=request.route_path('create_first_user'))

    # if openid_enabled is false we don't need to do any of the following. :(
    next_url = get_next_url(request, DEFAULT_DASHBOARD_NEXT)
    openid_url = "{0}?{1}".format(request.route_url('login_openid_begin'),
                                  url.urlencode({URL_PARAM_NEXT: next_url}))
    identifier = settings.get('identity_service_identifier', 'OpenID')
    external_hint = 'AeroFS user with no {} account?'.format(identifier)

    login = request.params.get(URL_PARAM_EMAIL)
    if not login: login = ''

    return {
        'url_param_email': URL_PARAM_EMAIL,
        'url_param_password': URL_PARAM_PASSWORD,
        'url_param_remember_me': URL_PARAM_REMEMBER_ME,
        'openid_enabled': openid_enabled,
        'openid_url': openid_url,
        'openid_service_identifier': identifier,
        'openid_service_external_hint': external_hint,
        'login': login,
        'is_private_deployment': is_private_deployment(request.registry.settings)
    }


def _has_users(settings):
    if not is_private_deployment(settings):
        # Public deployment always has users populated
        return True

    # Once there are any users created, we use the cached value (which is always True) to avoid
    # SP lookups. N.B. we assume that the last user in the system cannot delete himself.
    global _has_users_cache
    if not _has_users_cache:
        log.info('has_users not cached. ask SP for user count')
        ## This URL returns the number of existing users in JSON. See SPServlet.java:licenseCheck()
        r = requests.get(settings['deployment.sp_server_uri'] + '/license')
        r.raise_for_status()
        _has_users_cache = r.json()['users'] > 0

    return _has_users_cache


@view_config(
    route_name='login_for_tests.json',
    permission=NO_PERMISSION_REQUIRED,
    renderer='json'
)
def login_for_tests_json_view(request):
    """
    Login over JSON for system tests that need it. (Like the mobile apps)
    This method always return a valid JSON document (both for success and errors)
    """
    try:
        login = request.params[URL_PARAM_EMAIL]
        password = request.params[URL_PARAM_PASSWORD].encode("utf-8")
        try:
            headers = _log_in_user(request, login, password, False)
            request.response.headerlist.extend(headers)
            return {'ok': True}
        except ExceptionReply as e:
            if e.get_type() == PBException.BAD_CREDENTIAL:
                request.response.status = 401
                return {'error': '401', 'message': 'unauthorized'}
            else:
                raise
    except Exception as e:
        log.exception(e)
        request.response.status = 500
        return {'error': '500', 'message': 'internal server error'}


def _log_in_user(request, login, creds, stay_signed_in):
    """
    Logs in the given user with the given credentials and returns a
    set of headers to create a session for the user. Could potentially throw any
    protobuf exception that SP throws.

    creds is expected to be converted to bytes (we are using utf8)
    """
    if not isinstance(creds, bytes):
        raise TypeError("credentials require encoding")

    # ignore any session data that may be saved
    settings = request.registry.settings
    con = SyncConnectionService(settings['base.sp.url'], settings['sp.version'])
    sp = SPServiceRpcStub(con)

    sp.credential_sign_in(login, creds)

    if stay_signed_in:
        log.debug("Extending session")
        sp.extend_session()

    # TOOD (WW) consolidate how we save authorization data in the session
    # TODO (WW) move common code between login_view.py and openid.py to login_util.py
    request.session['sp_cookies'] = con._session.cookies
    request.session['team_id'] = sp.get_organization_id().org_id

    return remember(request, login)


@view_config(
    route_name='logout',
    permission='user'
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
    except Exception as e:
        # Server errors don't matter when logging out.
        log.warn("sp.sign_out failed. ignore", e)
        pass

    # Delete session information from client cookies.
    request.session.delete()

    return forget(request)


@view_config(
    route_name='dashboard_home',
    ## The target routes of the redirect below manage permissions
    permission=NO_PERMISSION_REQUIRED,
)
def dashboard_home(request):
    if is_private_deployment(request.registry.settings):
        redirect = 'files'
    else:
        # Don't redirect to the files page for public deployment as most users would have API
        # access disabled
        redirect = 'my_shared_folders'
    return HTTPFound(location=request.route_path(redirect))

