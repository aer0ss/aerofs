import logging
from pyramid.httpexceptions import HTTPOk
from pyramid.view import view_config
from base64 import b64encode
from os import urandom
from web.error import error
from web.views.maintenance.maintenance_util import get_conf_client, get_conf

log = logging.getLogger(__name__)


@view_config(
    route_name='monitoring',
    permission='maintain',
    renderer='monitoring.mako'
)
def monitoring(request):
    conf = get_conf(request)
    txt_str = conf['monitoring.username'] + ':' + conf['monitoring.password']
    return {
        'base_url': conf['base.www.url'],
        'username': conf['monitoring.username'],
        'password': conf['monitoring.password'],
        'base64_str': b64encode(txt_str),
        'pw_exists': conf['monitoring.password'] != ""
    }


@view_config(
    route_name='json_regenerate_monitoring_cred',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def json_regenerate_monitoring_cred(request):
    log.info("generate monitoring cred")

    conf = get_conf_client(request)
    # a simplified base64 alphabet to make sure this is easy to copy-paste:
    conf.set_external_property('monitoring_password', b64encode(urandom(24), 'ab'))

    return HTTPOk()
