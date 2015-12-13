from pyramid.view import view_config
from web.views.maintenance.maintenance_util import get_conf, get_conf_client
from web.util import str2bool
import logging

log = logging.getLogger(__name__)

def _is_customization_allowed(conf):
    """
    @return whether customization is allowed with this license
    """

    # is_trial is true on the free license, false on the other license.
    # need to return the opposite of is_trial
    return not str2bool(conf.get('license_is_trial', True))

@view_config(
    route_name='customization',
    permission='maintain',
    renderer='customization.mako',
    request_method='GET',
)
def customization(request):
    conf = get_conf(request)

    if not _is_customization_allowed(conf):
        request.override_renderer = 'customization_upgrade.mako'

    return {
        'maintenance_custom_banner_text': conf.get("customization.banner_text", ""),
    }

@view_config(
    route_name='customization',
    permission='maintain',
    renderer='json',
    request_method='POST',
)
def customization_post(request):

    # prevent user from directly calling this method if customizaiton is not allowed
    if not _is_customization_allowed(get_conf(request)):
        return {}

    customization_banner_text = _format_banner_text(request.params["customization_banner_text"])
    conf_client = get_conf_client(request)
    conf_client.set_external_property('customization_banner_text', customization_banner_text)

    #if enable white label logo is selected, and a white label logo is available, set it
    if str2bool(request.params['enable-white-label-logo']) and "white-label-logo" in request.params:
        b64_file = request.params["white-label-logo"]
        conf_client.set_external_property("white_label_logo", b64_file)
    #else if disable white label logo is selected, disable it
    elif not str2bool(request.params['white-label-logo']):
        conf_client.set_external_property("white_label_logo", "")
    #in all other cases do nothing


    return {}

def _format_banner_text(banner_text):
    return banner_text.strip().replace('\n', '').replace('\r', '')

