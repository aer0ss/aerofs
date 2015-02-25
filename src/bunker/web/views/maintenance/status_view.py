import logging
import json
import urllib2
from pyramid.view import view_config
from operator import itemgetter
from maintenance_util import has_external_db

log = logging.getLogger(__name__)

def _status_url(request):
    return request.registry.settings["deployment.status_server_uri"]

@view_config(
    route_name='status',
    permission='maintain',
    renderer='status.mako'
)
def status_view(request):
    return {
        'possible_backup': has_external_db(request.registry.settings),
        'server_statuses': _get_server_statuses(request)
    }

def _get_server_statuses(request):
    """
    Get server statuses, sorted by server name.
    """
    statuses = json.load(urllib2.urlopen(_status_url(request)))['statuses']
    return sorted(statuses, key=itemgetter('service'))
