import logging
import json
import urllib2
from pyramid.httpexceptions import HTTPBadRequest
from pyramid.view import view_config
from web.util import is_private_deployment

log = logging.getLogger(__name__)

@view_config(
    route_name='status',
    permission='maintain',
    renderer='status.mako'
)
def status_view(request):
    settings = request.registry.settings

    # Site setup is available only in private deployment
    if not is_private_deployment(request.registry.settings):
        raise HTTPBadRequest("the page is not available")

    url = settings['base.status_url.unified']
    return {
        'server_statuses': _get_server_status(url)
    }

def _get_server_status(url):
    return json.load(urllib2.urlopen(url))['statuses']
