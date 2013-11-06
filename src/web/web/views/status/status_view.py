import logging, json, urllib2
from pyramid.httpexceptions import HTTPBadRequest
from pyramid.view import view_config
from web.util import *

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

    # Site setup is available only in private deployment
    if not is_private_deployment(request.registry.settings):
        raise HTTPBadRequest("the page is not available")

    url = settings['base.status_url.unified']
    return {
        'server_statuses': _get_server_status(url)
    }

def _get_server_status(url):
    return json.load(urllib2.urlopen(url))['statuses']
