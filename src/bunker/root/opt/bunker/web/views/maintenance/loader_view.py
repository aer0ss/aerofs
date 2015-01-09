import logging
from pyramid.view import view_config
import requests

log = logging.getLogger(__name__)


@view_config(
    route_name='json-create-configuration-initialized-flag',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def create_configuration_initialized_flag(request):
    open('/var/aerofs/configuration-initialized-flag', 'w').close()
    return {}

####################
# Below are proxies to other services. Necessary as the other services have no authentication.
#


@view_config(
    route_name='json-boot',
    permission='maintain',
    renderer='json',
    request_method='GET'
)
def boot_get(request):
    """
    Get the boot id
    """
    return requests.get('http://loader.service/boot').json()


@view_config(
    route_name='json-boot',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def boot_post(request):
    """
    Reboot
    """
    requests.post('http://loader.service/boot')
    return {}


@view_config(
    route_name='json-repackaging',
    permission='maintain',
    renderer='json',
    request_method='GET'
)
def repackaging_get(request):
    """
    Get repackaging status
    """
    return requests.get('http://repackaging.service').json()


@view_config(
    route_name='json-repackaging',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def repackaging_post(request):
    """
    Launch repackaging
    """
    requests.post('http://repackaging.service')
    return {}

