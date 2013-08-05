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
from login_view import *

log = logging.getLogger(__name__)

def _log_in_user(request, login, creds, stay_signed_in):
    """
    Logs in the given user with the given hashed password (creds) and returns a
    set of headers to create a session for the user. Could potentially throw any
    protobuf exception that SP throws.
    """

    # ignore any session data that may be saved
    settings = request.registry.settings
    con = SyncConnectionService(settings['base.sp.url'], settings['sp.version'])
    sp = SPServiceRpcStub(con)

    sp.sign_in(login, creds)

    if stay_signed_in:
        log.debug("Extending session")
        sp.extend_session()

    request.session['sp_cookies'] = con._session.cookies
    request.session['username'] = login
    request.session['team_id'] = sp.get_organization_id().org_id
    reload_auth_level(request)

    return remember(request, login)

@view_config(
    route_name='login_credential',
    permission=NO_PERMISSION_REQUIRED,
    renderer='login.mako'
)
def cred_login(request):
    # FIXME: a handful of non-idiomatic python follows. Please clean up when the skies are clear.
    _ = request.translate
    settings = request.registry.settings

    next = request.params.get('next')

    if not next:
        next = request.url
        base_url = request.resource_url(request.context, '')
        login_url = request.resource_url(request.context, 'login')
        login_cred = request.resource_url(request.context, 'login_credential')

        if next == base_url or next == login_url or next == login_cred or len(next) == 0:
            # Never redirect to the login page itself.
            next = request.route_path('dashboard_home')

    login = ''
    # N.B. the all following parameter keys are used by signup.mako as well.
    # Keep them consistent!
    if URL_PARAM_FORM_SUBMITTED in request.params:
        # Remember to normalize the email address.
        login = request.params[URL_PARAM_EMAIL]
        password = request.params[URL_PARAM_PASSWORD]
        hashed_password = scrypt(password, login)
        stay_signed_in = URL_PARAM_REMEMBER_ME in request.params

        try:
            try:
                headers = _log_in_user(request, login, hashed_password, stay_signed_in)
                log.debug(login + " logged in")
                return HTTPFound(location=next, headers=headers)
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
            support_email = settings.get('base.www.support_email_address', 'support@aerofs.com')
            flash_error(request, _("An error occurred processing your request." +
                   " Please try again later. Contact {support_email} if the" +
                   " problem persists.", {'support_email': support_email }))

    return {
        'url_param_form_submitted': URL_PARAM_FORM_SUBMITTED,
        'url_param_email': URL_PARAM_EMAIL,
        'url_param_password': URL_PARAM_PASSWORD,
        'url_param_remember_me': URL_PARAM_REMEMBER_ME,
        'url_param_next': URL_PARAM_NEXT,
        'login': login,
        'next': next,
    }
