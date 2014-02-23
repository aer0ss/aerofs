import logging

from pyramid.view import view_config
from web.version import get_current_version
from web.views.maintenance.logs_view import get_file_download_response, get_download_file_name

log = logging.getLogger(__name__)

# The path is documented in db-backup.tasks
BACKUP_FILE_PATH = '/opt/bootstrap/public/aerofs-db-backup.tar.gz'
_DOWNLOAD_FILE_PREFIX = 'aerofs-backup_'
# Use no suffix to discourage users from unzipping the file
_DOWNLOAD_FILE_SUFFIX = ''

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
        'current_version': get_current_version()
    }

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