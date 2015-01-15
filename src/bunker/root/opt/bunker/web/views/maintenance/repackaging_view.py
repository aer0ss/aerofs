from pyramid.view import view_config
import requests

####################
# Below are proxies to other services. Necessary as the other services have no authentication.
#

@view_config(
    route_name='json-repackaging',
    permission='maintain',
    renderer='json',
    request_method='GET'
)
def get_repackaging(request):
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
def post_repackaging(request):
    """
    Launch repackaging
    """
    requests.post('http://repackaging.service')
    return {}

