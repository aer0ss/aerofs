from pyramid.view import view_config
import errno
import os
import requests

####################
# Below are proxies to other services. Necessary as the other services have no authentication.
#

REPACKAGE_DONE="/opt/repackaging/installers/modified/.repackage-done"

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


@view_config(
    route_name='json-del-repackage-done',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def post_del_repackage_done(request):
    """
    Delete repackage done flag file.
    """
    try:
        os.remove(REPACKAGE_DONE)
    except OSError as e:
        if e.errno != errno.ENOENT:
            raise
    return {}

