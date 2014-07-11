import base64
import datetime
import logging
from io import BytesIO

from aerofs_common.exception import ExceptionReply
from pyramid.httpexceptions import HTTPFound
from pyramid.response import Response
from pyramid.security import authenticated_userid
from pyramid.view import view_config
import qrcode

from web.util import get_rpc_stub, flash_error, flash_success
from web.crypto import crypto_box, crypto_box_open

log = logging.getLogger(__name__)

_URL_PARAM_CODE = "code"
_FORM_PARAM_BOXED_SECRET = "boxed_secret"

@view_config(
    route_name='two_factor_intro',
    permission='user',
    renderer='two_factor_intro.mako',
    request_method='GET',
)
def two_factor_intro(request):
    sp = get_rpc_stub(request)
    tfa_enforcing = False
    try:
        prefs = sp.get_user_preferences(None)
        tfa_enforcing = prefs.two_factor_enforced
    except Exception as e:
        # Silence exceptions; warning is opportunistic
        pass
    return {
            "enforcing": tfa_enforcing,
    }

@view_config(
    route_name='two_factor_setup',
    permission='user',
    request_method='GET',
)
def two_factor_setup_get(request):
    # two_factor_setup only allows POSTs, but do something polite if the user
    # enters the URL in their browser
    return HTTPFound(location=request.route_path('two_factor_intro'))

@view_config(
    route_name='two_factor_setup',
    permission='user',
    renderer='two_factor_setup.mako',
    request_method='POST',
)
def two_factor_setup_post(request):
    if _URL_PARAM_CODE in request.params:
        # The user is trying to prove they have successfully set up their two-factor auth app.
        # See if they got it right.
        try:
            claimed_code = int(request.params[_URL_PARAM_CODE], 10)
            sp = get_rpc_stub(request)
            try:
                reply = sp.set_two_factor_enforcement(True, claimed_code)
                # Two factor auth is now enabled!
                flash_success(request, "Two-factor authentication enabled.")
                return HTTPFound(location=request.route_path('two_factor_settings'))
            except ExceptionReply as e:
                if e.get_type_name() == "BAD_CREDENTIAL":
                    # Incorrect code provided.  Try again?
                    flash_error(request, "Incorrect confirmation code provided.  Rescan the QR code and try again.")
                    # Fall through; we'll extract the boxed secret below.
                else:
                    flash_error(request, "Something went wrong.  Try setting up again?")
                    return HTTPFound(location=request.route_path('two_factor_intro'))
        except:
            # If this fails, the user provided an invalid code.
            pass

    shared_secret = None
    if _FORM_PARAM_BOXED_SECRET in request.params:
        # If we were presented a boxed_secret, extract the shared secret from that.
        try:
            boxed_secret = base64.b64decode(request.params[_FORM_PARAM_BOXED_SECRET])
            shared_secret = crypto_box_open(request, boxed_secret)
        except ValueError as e:
            flash_error(request, "Invalid boxed secret - try setting up again.")
            return HTTPFound(location=request.route_path('two_factor_intro'))
    else:
        # Otherwise, we're enrolling anew - ask SP to generate us new codes.
        sp = get_rpc_stub(request)
        try:
            reply = sp.setup_two_factor()
            shared_secret = reply.secret
        except ExceptionReply as e:
            flash_error(request, "Something went wrong. Try again?")
            return HTTPFound(location=request.route_path('two_factor_intro'))

    # render
    secret = base64.b32encode(shared_secret)
    label = "AeroFS: {}".format(authenticated_userid(request))
    url = "otpauth://{}/{}?secret={}".format("totp", label, secret)
    qr = qrcode.QRCode(
        error_correction=qrcode.constants.ERROR_CORRECT_L,
        box_size=6,
        border=4,
    )
    qr.add_data(url)
    qr.make(fit=True)
    buf = BytesIO()
    qr.make_image().save(buf, "png")
    qr_image_data = "data:image/png;base64,{}".format(base64.b64encode(buf.getvalue()))

    # Encrypt the secret so that we can embed it in the POST form.  This way we
    # can generate the secret once, but still give the user multiple tries to
    # get it right without having to ask SP to generate a new secret.
    # I'm reusing the session key because it's a deployment-unique strong
    # random and will be fine for this purpose.
    secret_blob = crypto_box(request, shared_secret)

    return {
        'secret': secret,
        'secret_blob': base64.b64encode(secret_blob),
        'totpauth_url': url,
        'url_param_code': _URL_PARAM_CODE,
        'qr_image_data': qr_image_data
    }

@view_config(
    route_name='two_factor_disable',
    permission='user',
    request_method='POST',
)
def two_factor_disable_post(request):
    sp = get_rpc_stub(request)
    try:
        sp.set_two_factor_enforcement(False, None)
        flash_success(request, "Disabled two-factor authentication")
    except ExceptionReply as e:
        flash_error(request, "Failed to disable two-factor authentication")
    return HTTPFound(location=request.route_path('settings'))

@view_config(
    route_name='two_factor_settings',
    permission='user',
    renderer='two_factor_settings.mako',
    request_method='GET',
)
def two_factor_settings(request):
    sp = get_rpc_stub(request)
    reply = sp.get_user_preferences(None)
    two_factor_enforced = reply.two_factor_enforced
    backup_codes = []
    if two_factor_enforced:
        reply = sp.get_backup_codes()
        for code in reply.codes:
            backup_codes.append({
                "code": code.code,
                "date_used": code.date_used,
            })
    return {
        'two_factor_enforced': two_factor_enforced,
        'backup_codes': backup_codes,
    }

@view_config(
    route_name='two_factor_download_backup_codes',
    permission='user',
    request_method='GET',
)
def two_factor_download_backup_codes(request):
    sp = get_rpc_stub(request)
    reply = sp.get_backup_codes()
    body = ""
    for i, code in enumerate(reply.codes):
        used_text = ""
        if code.date_used != 0:
            used_text = " (used on {})".format(datetime.datetime.utcfromtimestamp(code.date_used / 1000).strftime("%Y-%m-%d"))
        body = body + "{}.\t{}{}\n".format(i + 1, code.code, used_text)
    resp = Response(body=body, status=200, content_type="text/plain; charset=utf-8")
    resp.content_disposition = "attachment; filename=aerofs_backup_codes.txt"
    return resp