import logging, json, urllib2
from aerofs_sp.gen.common_pb2 import PBException
from pyramid.httpexceptions import HTTPOk
from pyramid.view import view_config
from web.sp_util import exception2error
from web.util import *
from web.views.payment import stripe_util

log = logging.getLogger(__name__)

URL_PARAM_ORG_ID = 'org_id'
URL_PARAM_SHARE_ID = 'sid'
URL_PARAM_JOINED_TEAM_NAME = 'new_team'

@view_config(
    route_name='status',
    permission='user',
    renderer='status.mako'
)
def status_view(request):
    settings = request.registry.settings

    persistent_host = settings['status_hosts.persistent']
    transient_host = settings['status_hosts.transient']

    return {
        'persistent_server_statuses': get_server_statuses(request, persistent_host),
        'transient_server_statuses': get_server_statuses(request, transient_host)
    }

def get_server_statuses(request, host):
    return json.load(urllib2.urlopen('http://' + host + ':8000/'))['statuses']
