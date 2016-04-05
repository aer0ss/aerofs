import logging
import base64

from pyramid.httpexceptions import HTTPFound
from pyramid.view import view_config
import aerofs_sp.gen.common_pb2 as common

from web.analytics import send_analytics_event
from web.util import flash_error, flash_success, get_rpc_stub, str2bool

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
    route_name='org_settings',
    renderer='org_settings.mako',
    permission='admin',
    request_method='GET',
)
def org_settings(request):
    send_analytics_event(request, "ACTIVE_USER")
    sp = get_rpc_stub(request)

    reply = sp.get_org_preferences()

    show_quota = str2bool(request.registry.settings.get('show_quota_options', False))
    quota_reply = sp.get_quota()
    quota_enabled = quota_reply.HasField('quota')
    quota = _bytes2gb(quota_reply.quota) if quota_enabled else None

    return {
        'tfa_level': reply.level,
        'show_quota_options': show_quota,
        'quota_enabled': quota_enabled,
        'quota': quota
    }

@view_config(
    route_name='org_settings',
    permission='admin',
    request_method='POST',
)
def org_settings_post(request):
    send_analytics_event(request, "ACTIVE_USER")
    sp = get_rpc_stub(request)
    _update_org_settings(request, sp)
    return HTTPFound(location=request.route_path('org_settings'))


def _update_org_settings(request, sp):
    sp.set_two_factor_setup_enforcement(int(request.params['tfa-setting']))

    if str2bool(request.registry.settings.get('show_quota_options', False)):
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
            sp.set_quota(_gb2bytes(quota))

    flash_success(request, "Organization settings have been updated.")


def _bytes2gb(bytes):
    """
    This method rounds down the bytes to a integer GB amount.
    """
    return bytes / (1024 ** 3)


def _gb2bytes(gb):
    return gb * (1024 ** 3)
