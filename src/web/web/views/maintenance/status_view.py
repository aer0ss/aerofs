import logging
import json
import urllib2
from pyramid.view import view_config

log = logging.getLogger(__name__)

@view_config(
    route_name='manage',
    permission='maintain',
    renderer='status.mako'
)
@view_config(
    route_name='status',
    permission='maintain',
    renderer='status.mako'
)
def status_view(request):
    settings = request.registry.settings
    url = settings['base.status_url.unified']
    return {
        'server_statuses': _get_server_status(url)
    }

def _get_server_status(url):
    return json.load(urllib2.urlopen(url))['statuses']
