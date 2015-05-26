import logging
import json
import urllib2
from pyramid.view import view_config
from operator import itemgetter
import requests
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


# TODO (WW) the status page should call this API instead of the current approach
@view_config(
    route_name='json-status',
    permission='maintain',
    renderer='json',
)
def json_status(request):
    r = requests.get(_status_url(request))
    r.raise_for_status()
    return r.json()
