import logging

from pyramid.view import view_config
from web.version import get_current_version
from web.views.maintenance.logs_view import get_file_download_response

log = logging.getLogger(__name__)

# The path is documented in db-backup.tasks
BACKUP_FILE_PATH = '/opt/bootstrap/public/aerofs-db-backup.tar.gz'

@view_config(
    route_name='backup_appliance',
    permission='maintain',
    renderer='backup.mako'
)
def backup_appliance(request):
    return {}

@view_config(
    route_name='upgrade_appliance',
    permission='maintain',
    renderer='upgrade.mako'
)
def upgrade_appliance(request):
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

    # Use no prefix to discourage users from opening the file
    return get_file_download_response(BACKUP_FILE_PATH,
                                      'application/octet-stream',
                                      'aerofs-backup_', '')
