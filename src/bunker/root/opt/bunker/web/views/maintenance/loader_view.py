import json
import logging
import maintenance_util
import psutil
import requests

from os import unlink
from os.path import isfile
from pyramid.view import view_config
from subprocess import Popen
from sys import stdout, stderr
from threading import Thread
from web.error import expected_error
from web.version import get_private_version

log = logging.getLogger(__name__)

REG = "registry.aerofs.com"
LOADER_URL = 'http://loader.service/v1'


@view_config(
    route_name='json-set-configuration-completed',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def set_configuration_completed(request):
    maintenance_util.set_configuration_completed()
    return {}


@view_config(
    route_name='json-get-boot',
    permission='maintain',
    renderer='json',
    request_method='GET'
)
def get_boot(request):
    """
    Get the boot id
    """
    r = requests.get(LOADER_URL + '/boot')
    r.raise_for_status()
    return r.json()


@view_config(
    route_name='json-boot',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def post_boot(request):
    """
    Reboot
    """
    r = requests.post(LOADER_URL + '/boot/{}'.format(request.matchdict['target']))
    r.raise_for_status()
    return {}


def _latest_version_from_registry():
    r = requests.get("{}/tags/latest/{}".format(LOADER_URL, REG))
    r.raise_for_status()
    return str(r.json())


@view_config(
    route_name='json-needs-upgrade',
    permission='maintain',
    request_method='GET',
    renderer='json'
)
def json_needs_upgrade_get(request):
    current_version = get_private_version(request.registry.settings)
    latest = _latest_version_from_registry()
    log.info("GET needs upgrade current {} latest{}".format(current_version, latest))
    return {
        'needs-upgrade': latest != current_version
    }


@view_config(
    route_name='json-switch-appliance',
    permission='maintain',
    request_method='POST',
    renderer='json'
)
def json_switch_appliance_post(request):
    latest = _latest_version_from_registry()
    log.info("Switching to appliance {}".format(latest))
    r = requests.post("{}/switch/{}/{}/default".format(LOADER_URL, REG, latest))
    r.raise_for_status()
    return {}


def _pull_images_status():
    r = requests.get("{}/images/pull".format(LOADER_URL))
    r.raise_for_status()
    return json.loads(r.text)


@view_config(
    route_name='json-pull-images',
    permission='maintain',
    request_method='POST',
    renderer='json'
)
def json_pull_images_post(request):
    pull_stats = _pull_images_status()
    if pull_stats.get("status", "") == "pulling":
        expected_error('Upgrade in progress')
    latest = _latest_version_from_registry()
    log.info("Pull new images for version {}".format(latest))

    r = requests.post("{}/images/pull/{}/{}".format(LOADER_URL, REG, latest))
    if r.status_code == 409:
        log.info("A pull is already in progress.")
    elif r.status_code != 200:
        log.info("Pulling images failed with message: {}".format(json.loads(r.text)))

    return {"status_code": r.status_code}


@view_config(
    route_name='json-pull-images',
    permission='maintain',
    request_method='GET',
    renderer='json'
)
def json_pull_images_get(request):
    pull_status = _pull_images_status()
    status = pull_status.get("status", "error")
    if status == "error":
        log.info("Failed to pull images: {}".format(pull_status.get("message", "")))
    else:
        stats = "all images pulled" if status == "done" else "{} of {}".format(pull_status.get("pulled", -1), pull_status.get("total", -1))
        log.info("Pull images status, status {} ({})".format(status, stats))

    return {
        'running': status == "pulling",
        'succeeded': status == "done",
        'pulling': pull_status.get("pulled", -1),
        'total': pull_status.get("total", -1)
    }


@view_config(
    route_name='json-gc',
    permission='maintain',
    request_method='POST',
    renderer='json'
)
def json_gc_post(request):
    log.info("Clean old images POST")
    r = requests.post("{}/gc".format(LOADER_URL))
    r.raise_for_status()
    return {"status_code": r.status_code}


@view_config(
    route_name='json-gc',
    permission='maintain',
    request_method='GET',
    renderer='json'
)
def json_gc_get(request):
    r = requests.get("{}/gc".format(LOADER_URL))
    r.raise_for_status()
    status = r.json().get("status", "error")
    return {
        'running': status == "cleaning",
        'succeeded': status == "done"
    }

