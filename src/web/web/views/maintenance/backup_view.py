from datetime import datetime
import logging
from pyramid.response import Response

from pyramid.view import view_config

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
    return {}

@view_config(
    route_name = 'download_backup_file',
    permission='maintain',
    request_method = 'GET'
)
def download_backup_file(request):
    """
    Return the backup file's content as the response.
    Call this method once backup is done.

    Also see http://docs.pylonsproject.org/projects/pyramid_cookbook/en/latest/static_assets/files.html for alternatives to send file content as responses.
    """
    # The browser will use this name as the name for the downloaded file
    name = 'aerofs-backup_{}.dat'.format(datetime.today().strftime('%Y%m%d-%H%M%S'))
    f = open(BACKUP_FILE_PATH)
    return Response(content_type='application/x-compressed', app_iter=f,
                    content_disposition='attachment; filename={}'.format(name))