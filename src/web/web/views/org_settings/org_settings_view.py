import logging, base64
from pyramid.view import view_config
import aerofs_sp.gen.common_pb2 as common
from web.util import flash_error, flash_success, get_rpc_stub, \
    parse_rpc_error_exception, str2bool
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
    route_name='start_subscription',
    renderer='org_settings.mako',
    permission='admin'
)
def start_subscription(request):
    ret = org_settings(request)
    # The page respects this flag only if not has_customer_id
    ret['upgrade'] = True
    return ret


@view_config(
    route_name='org_settings',
    renderer='org_settings.mako',
    permission='admin'
)
def org_settings(request):
    sp = get_rpc_stub(request)

    # Only POST requests can modify state. See README.security.txt
    if request.method == 'POST':
        _update_org_settings(request, sp)

    reply = sp.get_org_preferences()

    show_quota = str2bool(request.registry.settings['show_quota_options'])
    quota_reply = sp.get_quota()
    quota_enabled = quota_reply.HasField('quota')
    quota = quota_reply.quota if quota_enabled else None

    # Show billing links only if the user has a Stripe customer ID
    stripe_data = sp.get_stripe_data().stripe_data

    return {
        'organization_name': reply.organization_name,
        'has_customer_id': stripe_data.customer_id,
        'stripe_publishable_key': STRIPE_PUBLISHABLE_KEY,
        'url_param_stripe_card_token': URL_PARAM_STRIPE_CARD_TOKEN,
        'show_quota_options': show_quota,
        'quota_enabled': quota_enabled,
        'quota': quota
    }


def _update_org_settings(request, sp):
    sp.set_org_preferences(request.params['organization_name'].strip(), None)

    if not 'enable_quota' in request.params:
        sp.remove_quota()
    else:
        try:
            quota = long(request.params['quota'])
            if quota < 0:
                flash_error(request, "Please input a positive number for quota")
                return
        except ValueError:
            flash_error(request, "Please input a valid quota number")
            return
        sp.set_quota(quota)

    flash_success(request, "Organization settings have been updated.")

