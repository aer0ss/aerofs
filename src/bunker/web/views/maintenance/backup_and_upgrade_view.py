import logging
import os
import psutil

from pyramid.response import Response
from pyramid.view import view_config
from subprocess import Popen
from web.version import get_private_version
from web.views.maintenance.logs_view import get_file_download_response, get_download_file_name
from web.views.maintenance.maintenance_util import get_conf
from os.path import isfile
from web.error import expected_error
from os import unlink
from sys import stdout, stderr

log = logging.getLogger(__name__)

# The path is documented in db-backup.tasks
BACKUP_FILE_PATH = '/opt/bootstrap/public/aerofs-db-backup.tar.gz'
BACKUP_SCRIPT_PATH = '/opt/bunker/web/content/backup_script.sh'
_DOWNLOAD_SCRIPT_FILENAME = 'aerofs-backup-script.sh'

_DOWNLOAD_FILE_PREFIX = 'aerofs-backup_'

# Use no suffix to discourage users from unzipping the file
_DOWNLOAD_FILE_SUFFIX = ''

# In theory it's not safe to have backup and restore procedure to share the same done file.
# In practice backup and restore can never happen at the same time.
_DONE_FILE = '/backup-or-restore-done'

_WRAPPER = '/backup-restore-wrapper.sh'

# As of right now (1/2016) our containers use roughly 1.5G-2G of disk space.
# Give a conservative limit of needing 10G to upgrade. (in KB)
_REQ_UPGRADE_DF = 10485760


@view_config(
    route_name='backup_and_upgrade',
    permission='maintain',
    renderer='backup_and_upgrade.mako'
)
def backup_and_upgrade(request):
    conf = get_conf(request)
    return {
        'os_upgrade_enabled': conf.get('os.upgrade.enabled', "false") == "true",
        'current_version': get_private_version(request.registry.settings)
    }

@view_config(
    route_name='json-has-disk-space',
    permission='maintain',
    request_method='GET',
    renderer='json'
)
def json_has_disk_space(request):
    s = os.statvfs('/')
    df_kb = (s.f_bavail * s.f_frsize) / 1024
    log.info("has_disk_space {} {}".format(df_kb, _REQ_UPGRADE_DF))
    return {
        'has_disk_space': df_kb >= _REQ_UPGRADE_DF
    }

@view_config(
    route_name='json-backup',
    permission='maintain',
    request_method='POST',
    renderer='json'
)
def json_backup_post(request):
    _backup_or_restore('backup')


@view_config(
    route_name='json-restore',
    permission='maintain',
    request_method='POST',
    renderer='json'
)
def json_restore_post(request):
    _backup_or_restore('restore')


def _backup_or_restore(action):
    """
    Note: repackaging/api/main.py shares very similar logic.
    """
    log.info("Running {}...".format(action))
    if is_running():
        expected_error('backup/restore process is already running')

    if isfile(_DONE_FILE):
        unlink(_DONE_FILE)

    Popen([_WRAPPER, action, BACKUP_FILE_PATH, _DONE_FILE], stdout=stdout.fileno(), stderr=stderr.fileno())

    return {}


@view_config(
    route_name='json-backup',
    permission='maintain',
    request_method='GET',
    renderer='json'
)
@view_config(
    route_name='json-restore',
    permission='maintain',
    request_method='GET',
    renderer='json'
)
def json_backup_or_restore_get(request):
    """
    Note: repackaging/api/main.py shares very similar logic.
    """
    running = is_running()
    succeeded = not running and isfile(_DONE_FILE)
    log.info("Get backup/restore status: running {}, succeeded {}".format(running, succeeded))

    return {
        'running': running,
        'succeeded': succeeded
    }


def is_running():
    for pid in psutil.pids():
        cmdline = psutil.Process(pid).cmdline()
        # The expected cmdline is [ '/bin/bash', _WRAPPER, ... ]
        if len(cmdline) > 1 and cmdline[1] == _WRAPPER:
            return True
    return False


@view_config(
    route_name='download_backup_file',
    permission='maintain',
    request_method='GET'
)
def download_backup_file(request):
    """
    Return the backup file's content as the response. Call this method once
    backup is done.
    """

    return get_file_download_response(BACKUP_FILE_PATH,
                                      'application/octet-stream',
                                      _DOWNLOAD_FILE_PREFIX, _DOWNLOAD_FILE_SUFFIX)


def example_backup_download_file_name():
    """
    Return an example backup download file name
    """
    return get_download_file_name(_DOWNLOAD_FILE_PREFIX, _DOWNLOAD_FILE_SUFFIX)

    route_name='download_backup_file',
    permission='maintain',
    request_method='GET'

@view_config(
    route_name='download_backup_script',
    permission='maintain',
    request_method='GET'
)
def download_backup_script(request):
    """
    Return the backup script as the response.
    """

    f = open(BACKUP_SCRIPT_PATH)
    return Response(content_type='application/octet-stream', app_iter=f,
                    content_disposition='attachment; filename={}'.format(_DOWNLOAD_SCRIPT_FILENAME))
