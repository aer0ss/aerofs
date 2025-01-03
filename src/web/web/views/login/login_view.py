import logging
import os

from pyramid import url
from pyramid.httpexceptions import HTTPFound
from pyramid.security import forget, NO_PERMISSION_REQUIRED
from pyramid.view import view_config
from aerofs_common.exception import ExceptionReply

from aerofs_sp.gen.common_pb2 import PBException
import requests
from web.util import flash_error, get_rpc_stub, str2bool, is_configuration_completed

from web.login_util import get_next_url, URL_PARAM_NEXT, redirect_to_next_page, \
        log_in_user, resolve_next_url

DEFAULT_DASHBOARD_NEXT = 'dashboard_home'

URL_PARAM_EMAIL = 'email'
URL_PARAM_PASSWORD = 'password'
URL_PARAM_REMEMBER_ME = 'remember_me'
URL_PARAM_CODE = 'code'

log = logging.getLogger(__name__)

# A boolean indicating whether the system has created one or more users.
_has_users_cache = False


def _is_openid_enabled(request):
    """
    True if the local deployment allows OpenID authentication
    """
    return request.registry.settings.get('lib.authenticator', 'local_credential').lower() == 'openid'


def _is_saml_enabled(request):
    """
    True if the local deployment allows OpenID authentication
    """
    return request.registry.settings.get('lib.authenticator', 'local_credential').lower() == 'saml'


def _do_login(request):
    """
    Log in the user. Return HTTPFound if login is successful; otherwise call
    flash_error() to insert error messages and then return None.
    """
    _ = request.translate

    # Remember to normalize the email address.
    userid = request.params[URL_PARAM_EMAIL]
    password = request.params[URL_PARAM_PASSWORD].encode("utf-8")
    stay_signed_in = URL_PARAM_REMEMBER_ME in request.params
    try:
        try:
            headers, second_factor_required, second_factor_setup_required = log_in_user(request, _sp_cred_signin, userid=userid,
                    password=password, stay_signed_in=stay_signed_in)
            return redirect_to_next_page(request, headers, second_factor_required, second_factor_setup_required, DEFAULT_DASHBOARD_NEXT)
        except ExceptionReply as e:
            if e.get_type() == PBException.BAD_CREDENTIAL:
                log.warn(userid + " attempts to login w/ bad password")
                flash_error(request, _("Email or password is incorrect."))
            elif e.get_type() == PBException.EMPTY_EMAIL_ADDRESS:
                flash_error(request, _("The email address cannot be empty."))
            elif e.get_type() == PBException.PASSWORD_EXPIRED:
                log.warn(userid + " attempts to log in with expired password.")
                return HTTPFound(location=request.route_path('request_password_reset',
                        _query={"password_expired": True, "userid": userid}))
            elif e.get_type() == PBException.LICENSE_LIMIT:
                # When a user logs in with a non-local credential for the first time this actually
                # does a license check. We might want to handle this nicely on a different page, but
                # for now we should at least do the right thing with the exception.
                # TODO dedupe code in signup_view.py
                support_email = request.registry.settings.get('base.www.support_email_address')
                log.warn(userid + " attempts to login with non-local cred hitting license limit.")
                flash_error(request,
                        "Adding your user account would cause your organization to exceed its " +
                        "licensed user limit. Please contact your administrator at " +
                        "{}.".format(support_email))
            elif e.get_type() == PBException.NO_INVITE:
                support_email = request.registry.settings.get('base.www.support_email_address')
                log.warn(userid +  " attempts to login with non-local cred without invitation.")
                flash_error(request,
                            "You have not received an invitation to join AeroFS. " +
                            "Please contact you administrator at " +
                            "{}".format(support_email))
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
    settings = request.registry.settings
    mng_url = 'https://' + str(settings['base.host.unified']) + '/admin'
    _ = request.translate

    if request.method == "POST":
        ret = _do_login(request)
        if ret: return ret

    openid_enabled = _is_openid_enabled(request)
    saml_enabled = _is_saml_enabled(request)
    disable_remember_me = str2bool(settings.get('web.session_daily_expiration', False))
    open_signup = settings.get('signup_restriction', "USER_INVITED") == "UNRESTRICTED"
    external_login_enabled = settings.get('lib.authenticator', 'local_credential').lower() == 'external_credential'

    if not is_configuration_completed():
        return HTTPFound(location=mng_url)

    if not openid_enabled and not saml_enabled and not _has_users(settings):
        log.info('no users yet. ask to create the first user')
        return HTTPFound(location=request.route_path('create_first_user'))

    if not openid_enabled and not saml_enabled:
        ext_auth_login_url, ext_auth_display_user_pass_login, \
            ext_auth_login, ext_auth_identifier, external_hint = "","","","",""

    else:
        next_url = get_next_url(request, DEFAULT_DASHBOARD_NEXT)
        ext_auth_display_user_pass_login = settings.get('lib.display_user_pass_login', True)
        if ext_auth_display_user_pass_login:
            ext_auth_display_user_pass_login = str2bool(ext_auth_display_user_pass_login)
        else:
            # default to True if config returns a empty string.
            ext_auth_display_user_pass_login = True
        ext_auth_login = request.params.get(URL_PARAM_EMAIL)
        if not ext_auth_login: ext_auth_login = ''

        if openid_enabled:
            ext_auth_identifier = settings.get('identity_service_identifier', 'OpenID')
        elif saml_enabled:
            ext_auth_identifier = settings.get('saml.identity.service.identifier', 'SAML')

        ext_auth_login_begin_url = request.route_path('login_ext_auth_begin')
        ext_auth_login_url = "{0}?{1}".format(ext_auth_login_begin_url,
                url.urlencode({URL_PARAM_NEXT: next_url}))
        external_hint = 'AeroFS user with no {} account?'.format(ext_auth_identifier)

    return {
        'url_param_email': URL_PARAM_EMAIL,
        'url_param_password': URL_PARAM_PASSWORD,
        'url_param_remember_me': URL_PARAM_REMEMBER_ME,
        'openid_enabled': openid_enabled,
        'saml_enabled': saml_enabled,
        'ext_auth_login_url': ext_auth_login_url,
        'ext_auth_service_identifier': ext_auth_identifier,
        'ext_auth_service_external_hint': external_hint,
        'ext_auth_display_user_pass_login': ext_auth_display_user_pass_login,
        'ext_auth_login': ext_auth_login,
        'disable_remember_me': disable_remember_me,
        'external_login_enabled': external_login_enabled,
        'open_signup': open_signup,
    }

def _is_restored_from_backup(conf):
    return str2bool(conf['restored_from_backup'])


def _has_users(settings):
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
        userid = request.params[URL_PARAM_EMAIL]
        password = request.params[URL_PARAM_PASSWORD].encode("utf-8")
        try:
            headers, second_factor_needed, second_factor_setup_needed = log_in_user(request, _sp_cred_signin, userid=userid, password=password)
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

@view_config(
    route_name='login_second_factor',
    permission='two_factor_login',
    renderer='login_second_factor.mako',
    request_method='GET',
)
def login_second_factor_get(request):
    """
    Allow the user to attempt to provide a second factor for their login.
    """
    return {
            'url_param_code': URL_PARAM_CODE,
    }

@view_config(
    route_name='login_second_factor',
    permission='two_factor_login',
    renderer='login_second_factor.mako',
    request_method='POST',
)
def login_second_factor_post(request):
    _ = request.translate

    try:
        code = int(request.POST.get(URL_PARAM_CODE))
    except ValueError:
        flash_error(request, _("Please enter an integer"))
        return HTTPFound(location=request.current_route_path())

    sp = get_rpc_stub(request)
    # Verify second factor correctness.
    # If correct, redirect to next
    # If incorrect, flash message and redirect to self
    try:
        sp.provide_second_factor(code)
    except ExceptionReply as e:
        log.info("SP rejected attempt to provide second factor: {}".format(e.get_type_name()))
        if e.get_type_name() == "SECOND_FACTOR_REQUIRED":
            flash_error(request, _("That authentication code was invalid"))
        elif e.get_type_name() == "NOT_AUTHENTICATED":
            # TODO: send user to basic login page
            flash_error(request, _("You need to log in first"))
        elif e.get_type_name() == "NOT_FOUND":
            flash_error(request, _("You're not enrolled in two-factor auth."))
        else:
            flash_error(request, _("An unexpected error occured"))
        return HTTPFound(location=request.current_route_path())
    return HTTPFound(resolve_next_url(request, DEFAULT_DASHBOARD_NEXT))

@view_config(
    route_name='login_backup_code',
    permission='two_factor_login',
    renderer='login_backup_code.mako',
    request_method='GET',
)
def login_backup_code_get(request):
    """
    Allow the user to attempt to provide a two-factor backup code for their login.
    """
    return {
            'url_param_code': URL_PARAM_CODE,
    }

@view_config(
    route_name='login_backup_code',
    permission='two_factor_login',
    renderer='login_backup_code.mako',
    request_method='POST',
)
def login_backup_code_post(request):
    _ = request.translate

    code = request.POST.get(URL_PARAM_CODE)
    sp = get_rpc_stub(request)
    try:
        sp.provide_backup_code(code)
    except ExceptionReply as e:
        log.info("SP rejected attempt to provide backup code: {}".format(e.get_type_name()))
        if e.get_type_name() == "SECOND_FACTOR_REQUIRED":
            flash_error(request, _("That backup code was invalid or has already been used"))
        elif e.get_type_name() == "NOT_AUTHENTICATED":
            flash_error(request, _("You need to provide your first factor before your second"))
        elif e.get_type_name() == "NOT_FOUND":
            flash_error(request, _("You're not enrolled in two-factor auth."))
        else:
            flash_error(request, _("An unexpected error occurred"))
        return HTTPFound(location=request.current_route_path())
    return HTTPFound(resolve_next_url(request, DEFAULT_DASHBOARD_NEXT))

def _sp_cred_signin(request, sp_rpc_stub, **kw_args):
    """
    Inner function for use with login_util.log_in_user.

    userid is a string
    password is expected to be converted to bytes (we are using utf8)
    """
    userid = kw_args['userid']
    password = kw_args['password']
    if not isinstance(password, bytes):
        raise TypeError("credentials require encoding")
    result = sp_rpc_stub.credential_sign_in(userid, password)
    need_second_factor = result.HasField('need_second_factor') and result.need_second_factor
    need_second_factor_setup = result.HasField('need_second_factor_setup') and result.need_second_factor_setup
    log.debug('Credential login succeeded for {}{}'.format(userid,
        ", need second factor" if need_second_factor else ""))
    return userid, need_second_factor, need_second_factor_setup

@view_config(
    route_name='logout',
    permission=NO_PERMISSION_REQUIRED
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
    # redirect user to My Files page
    return HTTPFound(location=request.route_path('files'), headers=request.response.headers)

