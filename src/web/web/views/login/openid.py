import logging
from pyramid import url
from pyramid.httpexceptions import HTTPFound
from pyramid.security import NO_PERMISSION_REQUIRED
from pyramid.view import view_config

from aerofs_sp.gen.sp_pb2 import SPServiceRpcStub
from aerofs_sp.connection import SyncConnectionService
from aerofs_common.exception import ExceptionReply

from web.login_util import resolve_next_url, log_in_user
from web.util import flash_error
from web.views.login.login_view import DEFAULT_DASHBOARD_NEXT

log = logging.getLogger(__name__)

_SESSION_KEY_NEXT = 'openid_login_next'

def _sp_openid_get_session_attrs(request, sp_rpc_stub, **kw_args):
    """Inner function for log_in_user; see login_util.py"""
    session_nonce = kw_args['sp_session_nonce']
    attrs = sp_rpc_stub.open_id_get_session_attributes(session_nonce)
    if len(attrs.userId) == 0:
        log.error('Session nonce is not logged in: ' + session_nonce)
        settings = request.registry.settings
        support_email = settings.get('base.www.support_email_address', 'support@aerofs.com')
        flash_error(request, request.translate("An error occurred processing your"
            " authentication request. Please try again, or contact {support_email}"
            " if the problem persists.", {'support_email': support_email}))
        raise ExceptionReply('Authentication error')
    userid = attrs.userId
    need_second_factor = attrs.HasField('need_second_factor') and attrs.need_second_factor
    log.debug('OpenID login succeeded for {}{}'.format(userid,
        ", need second factor" if need_second_factor else ""))
    return userid, need_second_factor

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
    log.debug("begin openid: redirecting to {}".format(identity_url))
    return HTTPFound(location=identity_url)

@view_config(
    route_name='login_openid_complete',
    permission=NO_PERMISSION_REQUIRED,
)
def login_openid_complete(request):
    """
    Complete the signin procedure with SP.
    """
    session_nonce = request.session['sp_session_nonce']
    headers, second_factor_needed = log_in_user(request, _sp_openid_get_session_attrs,
            stay_signed_in=True, sp_session_nonce=session_nonce)
    # Note: this logic is very similar to login_util.py:redirect_to_next_page()
    redirect = request.session[_SESSION_KEY_NEXT]
    log.debug("openid login redirect to {}".format(redirect))
    return HTTPFound(location=redirect, headers=headers)
