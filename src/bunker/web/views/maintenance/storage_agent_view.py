import logging
import requests
import shutil
import os

from pyramid.view import view_config
from aerofs_sa.config import bundle_creator
from web.util import get_deployment_secret
from web.views.maintenance.logs_view import get_file_download_response
from maintenance_util import get_conf, get_conf_client
from .setup_storage_agent import setup_storage_agent

log = logging.getLogger(__name__)

_DOWNLOAD_FILE_PREFIX = 'aerofs-storage-setup_'
# Use no suffix to discourage users from unzipping the file
_DOWNLOAD_FILE_SUFFIX = ''
_DOWNLOAD_DIR = '/opt/bootstrap/public'
_ONBOARD_BUNDLE_DIR = '/aerofs-storage/'

# make sure these are in line with the values in StorageAgentMain.java
_SA_ROOTANCHOR = '/aerofs-storage/AeroFS'
_SA_RTROOT = '/aerofs-storage/.aerofs-storage-agent'
_offboard_bundle_location = ''

@view_config(
    route_name='storage_agent_setup',
    permission='maintain',
    renderer='storage_agent.mako'
)
def setup_view(request):
    conf = get_conf(request)
    onboard_settings = {}
    for k, v in conf.iteritems():
        if k.startswith("onboard"):
            onboard_settings[k] = v
    return {
        'onboard_settings' : onboard_settings,
    }


@view_config(
    route_name='enable_onboard_sa',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def enable_sa(request):
    print "enabling onboard sa"
    enable = request.params['enabled']
    config = get_conf_client(request)
    config.set_external_property('onboard_storage_enabled', enable.lower() == "true")


@view_config(
    route_name='config_onboard_sa',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def onboard_setup(request):
    print "configuring onboard SA"
    sa_properties = {k: v for k,v in request.params.iteritems() if k.startswith("sa_")}
    config = get_conf_client(request)
    for property, value in sa_properties.iteritems():
        config.set_external_property(property, value)
    sa_properties['sa_force_port'] = 8484
    bundle = create_bundle(get_token(request), request.registry.settings['deployment.config_server_uri'],
                           sanitize_properties(sa_properties, request.registry.settings), True)
    setup_storage_agent(bundle)


@view_config(
    route_name='make_config_bundle',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def offboard_bundle(request):
    # TODO validate the request.params, see if they need any sort of encoding
    global _offboard_bundle_location
    _offboard_bundle_location = create_bundle(get_token(request), request.registry.settings['deployment.config_server_uri'],
                           sanitize_properties(request.params, request.registry.settings), False)


@view_config(
    route_name='download_config_bundle',
    permission='maintain',
    request_method='GET'
)
def download_bundle(request):
    if not _offboard_bundle_location:
        raise Exception("no bundle to download")
    return get_file_download_response(_offboard_bundle_location, 'application/octet-stream', _DOWNLOAD_FILE_PREFIX, _DOWNLOAD_FILE_SUFFIX)


@view_config(
    route_name='clear_stored_data',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def clear_storage(request):
    if not os.path.exists(_SA_ROOTANCHOR):
        return
    for f in os.listdir(_SA_ROOTANCHOR):
        abspath = os.path.join(_SA_ROOTANCHOR, f)
        if os.path.isdir(abspath):
            shutil.rmtree(abspath)
        else:
            os.remove(abspath)


def get_token(request):
    headers = {
        "Authorization": "Aero-Service-Shared-Secret bunker {}".format(get_deployment_secret(request.registry.settings))
    }
    private_org_id = 2
    token_resp = requests.post('http://sparta.service:8085/v1.4/organizations/' + str(private_org_id) + '/storage_agent',
                               headers = headers)
    token_resp.raise_for_status()
    return token_resp.json()['token']


def create_bundle(token, config, properties, onboard):
    bundle = bundle_creator(token, config, properties, _ONBOARD_BUNDLE_DIR if onboard else _DOWNLOAD_DIR)
    return bundle.write_files()


# all properties for the storage agent get submitted with the sa_ prefix
def sanitize_properties(props, config):
    ret = {}
    for k, v in props.iteritems():
        if k.startswith("sa_"):
            ret[k[3:]] = v

    ret['root'] = _SA_ROOTANCHOR
    ret['rtroot'] = _SA_RTROOT
    ret['contact_email'] = config['base.www.support_email_address']
    return ret
