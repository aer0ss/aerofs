import logging
from pyramid import url
from pyramid.httpexceptions import HTTPFound
from pyramid.security import remember, NO_PERMISSION_REQUIRED
from pyramid.view import view_config

from aerofs_sp.gen.sp_pb2 import SPServiceRpcStub
from aerofs_sp.connection import SyncConnectionService
from aerofs_common.exception import ExceptionReply

from web.login_util import DEFAULT_DASHBOARD_NEXT, resolve_next_url
from web.util import flash_error

log = logging.getLogger(__name__)

_SESSION_KEY_NEXT = 'openid_login_next'

def _begin_sp_auth(request):
    """
    Get a session and delegate nonce from SP; returns an identity servlet
    URL that includes session nonce and final 'next' URL.
    NOTE: we save the "next" param from request in the session, and
    tell the IdentityServlet where to send the user when authentication is
    complete. In the web world, the "oncomplete" link is back to this
    component, in login_openid_complete.
    """
    settings = request.registry.settings
    con = SyncConnectionService(settings['base.sp.url'], settings['sp.version'])
    sp = SPServiceRpcStub(con)

    nonces = sp.open_id_begin_transaction()
    request.session['sp_session_nonce'] = nonces.sessionNonce
    request.session[_SESSION_KEY_NEXT] = resolve_next_url(request, DEFAULT_DASHBOARD_NEXT)
    _next = request.route_url('login_openid_complete')

    _url = "{0}/oa?{1}".format(settings['openid.service.url'],
        url.urlencode({
            'token': nonces.delegateNonce,
            'sp.oncomplete': _next}))
    return _url


def _get_sp_auth(request, stay_signed_in):
    """
    Ask SP to authenticate our session using our approved session nonce.
    Could potentially throw any protobuf exception that SP throws.
    @return the headers remember() returns
    """
    settings = request.registry.settings
    con = SyncConnectionService(settings['base.sp.url'], settings['sp.version'])
    sp = SPServiceRpcStub(con)

    session_nonce = request.session['sp_session_nonce']

    attrs = sp.open_id_get_session_attributes(session_nonce)

    if len(attrs.userId) == 0:
        log.error('Session nonce is not logged in: ' + session_nonce)
        support_email = settings.get('base.www.support_email_address', 'support@aerofs.com')
        flash_error(request, request.translate("An error occurred processing your"
            " authentication request. Please try again, or contact {support_email}"
            " if the problem persists.", {'support_email': support_email}))
        raise ExceptionReply('Authentication error')

    login = attrs.userId
    log.debug('SP auth returned ' + login)

    if stay_signed_in:
        log.debug("Extending session")
        sp.extend_session()

    # TOOD (WW) consolidate how we save authorization data in the session
    # TODO (WW) move common code between login_view.py and openid.py to login_util.py
    request.session['sp_cookies'] = con._session.cookies
    request.session['team_id'] = sp.get_organization_id().org_id

    return remember(request, login)

@view_config(
    route_name = 'login_openid_begin',
    permission=NO_PERMISSION_REQUIRED,
)
def login_openid(request):
    """
    Request nonces from SP. Store the session nonce and build an identity request url.
    Return the identity request url.
    """
    identity_url = _begin_sp_auth(request)
    return HTTPFound(location=identity_url)

@view_config(
    route_name='login_openid_complete',
    permission=NO_PERMISSION_REQUIRED,
)
def login_openid_complete(request):
    """
    Complete the signin procedure with SP.
    """
    headers = _get_sp_auth(request=request, stay_signed_in=True)
    # Note: this logic is very similar to login_util.py:redirect_to_next_page()
    redirect = request.session[_SESSION_KEY_NEXT]
    log.debug("openid login redirect to {}".format(redirect))
    return HTTPFound(location=redirect, headers=headers)
