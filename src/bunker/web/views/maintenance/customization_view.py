from pyramid.view import view_config
from web.views.maintenance.maintenance_util import get_conf, get_conf_client

@view_config(
    route_name='customization',
    permission='maintain',
    renderer='customization.mako',
    request_method='GET',
)
def customization(request):
    conf = get_conf(request)
    customization_banner_text = conf.get("customization.banner_text", "")

    return {
        'maintenance_custom_banner_text': customization_banner_text,
    }

@view_config(
    route_name='customization',
    permission='maintain',
    renderer='json',
    request_method='POST',
)
def customization_post(request):
    customization_banner_text = _format_banner_text(request.params["customization_banner_text"])
    conf_client = get_conf_client(request)
    conf_client.set_external_property('customization_banner_text', customization_banner_text)
    return {}

def _format_banner_text(banner_text):
    return banner_text.strip().replace('\n', '').replace('\r', '')

