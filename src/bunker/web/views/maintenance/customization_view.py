from pyramid.view import view_config
from web.views.maintenance.maintenance_util import get_conf, get_conf_client
from web.util import str2bool, is_group_view_enabled_nonadmin, is_user_view_enabled_nonadmin
import logging

log = logging.getLogger(__name__)

def _is_customization_allowed(conf):
    """
    @return whether customization is allowed with this license
    """

    # FIXME change to license_allow_enterprise_features. ENG-3372.
    return str2bool(conf.get('license_allow_auditing', True))

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

    settings = request.registry.settings

    return {
        'maintenance_custom_banner_text':   conf.get("customization.banner_text", ""),
        'customization_enable_group_view':  is_group_view_enabled_nonadmin(settings),
        'customization_enable_user_view':   is_user_view_enabled_nonadmin(settings)
    }

@view_config(
    route_name='customization',
    permission='maintain',
    renderer='json',
    request_method='POST',
)
def customization_post(request):

    # prevent user from directly calling this method if customization is not allowed
    if not _is_customization_allowed(get_conf(request)):
        return {}

    customization_banner_text = _format_banner_text(request.params["customization_banner_text"])
    conf_client = get_conf_client(request)
    conf_client.set_external_property('customization_banner_text', customization_banner_text)

    # process checkboxes
    enable_group_view = _validate_check_box(request, "customization_enable_group_view")
    enable_user_view  = _validate_check_box(request, "customization_enable_user_view")
    conf_client.set_external_property("customization_enable_group_view", enable_group_view)
    conf_client.set_external_property("customization_enable_user_view", enable_user_view)


    # if enable white label logo is selected, and a white label logo is available, set it
    if str2bool(request.params['enable-white-label-logo']) and "white-label-logo" in request.params:
        b64_file = request.params["white-label-logo"]
        conf_client.set_external_property("white_label_logo", b64_file)
    # else if disable white label logo is selected, disable it
    elif not str2bool(request.params['white-label-logo']):
        conf_client.set_external_property("white_label_logo", "")
    # in all other cases do nothing


    return {}

def _format_banner_text(banner_text):
    return banner_text.strip().replace('\n', '').replace('\r', '')

def _validate_check_box(request, param_name):
    request_value = request.params.getall(param_name)
    return True if request_value else False