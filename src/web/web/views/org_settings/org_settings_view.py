import logging, base64
from pyramid.view import view_config
import aerofs_sp.gen.common_pb2 as common
from web.util import flash_error, flash_success, get_rpc_stub, \
    parse_rpc_error_exception
from web.views.payment.stripe_util \
    import URL_PARAM_STRIPE_CARD_TOKEN, STRIPE_PUBLISHABLE_KEY

# URL param keys
URL_PARAM_USER = 'user'
URL_PARAM_FULL_NAME = 'full_name'

log = logging.getLogger(__name__)

def get_permission(pbrole):
    return common._PBROLE.values_by_number[int(pbrole)].name

def encode_store_id(sid):
    return base64.b32encode(sid)

def decode_store_id(encoded_sid):
    return base64.b32decode(encoded_sid)

@view_config(
    route_name = 'start_subscription',
    renderer = 'org_settings.mako',
    permission = 'admin'
)
def start_subscription(request):
    ret = team_settings(request)
    # The page respects this flag only if not has_customer_id
    ret['upgrade'] = True
    return ret

@view_config(
    route_name = 'org_settings',
    renderer = 'org_settings.mako',
    permission = 'admin'
)
def team_settings(request):
    _ = request.translate

    ret = {}
    sp = get_rpc_stub(request)

    # Only POST requests can modify state. See README.security.txt
    # TOOD (WW) separate POST and GET handling to different view callables
    if request.method == 'POST':
        form = {
            'organization_name': request.params['organization_name'].strip()
        }
        form.update(ret)

        try:
            sp.set_org_preferences(form['organization_name'], None)
            flash_success(request, _("Organization preferences have been updated."))
        except Exception as e:
            flash_error(request, parse_rpc_error_exception(request, e))

    reply = sp.get_org_preferences()
    ret['organization_name'] = reply.organization_name

    # Show billing links only if the user has a Stripe customer ID
    stripe_data = sp.get_stripe_data().stripe_data
    ret['has_customer_id'] = stripe_data.customer_id
    ret['stripe_publishable_key'] = STRIPE_PUBLISHABLE_KEY
    ret['url_param_stripe_card_token'] = URL_PARAM_STRIPE_CARD_TOKEN

    return ret
