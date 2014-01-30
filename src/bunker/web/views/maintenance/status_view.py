import logging
import json
import urllib2
from pyramid.view import view_config

log = logging.getLogger(__name__)

_STATUS_URL = 'http://localhost:8000'


@view_config(
    route_name='maintenance_home',
    permission='maintain',
    renderer='status.mako'
)
@view_config(
    route_name='status',
    permission='maintain',
    renderer='status.mako'
)
def status_view(request):
    return {
        'server_statuses': _get_server_statuses()
    }


def _get_server_statuses():
    return json.load(urllib2.urlopen(_STATUS_URL))['statuses']
