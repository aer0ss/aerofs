import logging

from pyramid.view import view_config
from subprocess import Popen
from web.version import get_private_version
from web.views.maintenance.logs_view import get_file_download_response, get_download_file_name
import psutil
from os.path import isfile
from web.error import error
from os import unlink
from sys import stdout, stderr

log = logging.getLogger(__name__)

# The path is documented in db-backup.tasks
BACKUP_FILE_PATH = '/opt/bootstrap/public/aerofs-db-backup.tar.gz'

_DOWNLOAD_FILE_PREFIX = 'aerofs-backup_'

# Use no suffix to discourage users from unzipping the file
_DOWNLOAD_FILE_SUFFIX = ''

# In theory it's not safe to have backup and restore procedure to share the same done file.
# In practice backup and restore can never happen at the same time.
_DONE_FILE = '/backup-or-restore-done'

_WRAPPER = '/backup-restore-wrapper.sh'

@view_config(
    route_name='backup',
    permission='maintain',
    renderer='backup.mako'
)
def backup(request):
    return {}

@view_config(
    route_name='upgrade',
    permission='maintain',
    renderer='upgrade.mako'
)
def upgrade(request):
    return {
        'current_version': get_private_version(request.registry.settings)
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
        error('backup/restore process is already running')

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
