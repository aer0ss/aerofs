import logging
from pyramid.view import view_config
import requests
import maintenance_util

log = logging.getLogger(__name__)

LOADER_URL = 'http://loader.service/v1'


@view_config(
    route_name='json-set-configuration-initialized',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def set_configuration_initialized(request):
    maintenance_util.set_configuration_initialized()
    return {}


@view_config(
    route_name='json-get-boot',
    permission='maintain',
    renderer='json',
    request_method='GET'
)
def get_boot(request):
    """
    Get the boot id
    """
    r = requests.get(LOADER_URL + '/boot')
    r.raise_for_status()
    return r.json()


@view_config(
    route_name='json-boot',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def post_boot(request):
    """
    Reboot
    """
    r = requests.post(LOADER_URL + '/boot/{}'.format(request.matchdict['target']))
    r.raise_for_status()
    return {}
