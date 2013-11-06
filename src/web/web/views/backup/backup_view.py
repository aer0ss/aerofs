from datetime import datetime
import logging
from pyramid.response import Response
from pyramid.security import NO_PERMISSION_REQUIRED

from pyramid.view import view_config
from aerofs_common import bootstrap
from aerofs_common.bootstrap import Status
from web.util import error

log = logging.getLogger(__name__)

# The path is documented in db-backup.tasks
_BACKUP_FILE_PATH = '/opt/bootstrap/public/aerofs-db-backup.tar.gz'

@view_config(
    route_name='backup',
    permission='admin',
    renderer='backup.mako'
)
def backup(request):
    return {}

@view_config(
    route_name='update',
    permission='admin',
    renderer='update.mako'
)
def update(request):
    return {}

@view_config(
    route_name = 'json_kickoff_backup',
    permission='admin',
    renderer = 'json',
    request_method = 'POST'
)
def json_kickoff_backup(request):
    log.info("kickoff db-backup")
    return {
        'bootstrap_execution_id': bootstrap.enqueue_task_set("db-backup"),
    }

@view_config(
    route_name = 'json_kickoff_maintenance_exit',
    # Since SP is down during backup, we can't authenticate the user
    # (see auth.py:get_principal(). TOOD (WW) use license auth
    permission=NO_PERMISSION_REQUIRED,
    renderer = 'json',
    request_method = 'POST'
)
def json_kickoff_maintenance_exit(request):
    log.info("kickoff maintenance-exit")
    return {
        'bootstrap_execution_id': bootstrap.enqueue_task_set("maintenance-exit")
    }

@view_config(
    route_name = 'json_poll_bootstrap',
    # Since SP is down during backup, we can't authenticate the user
    # (see auth.py:get_principal(). TOOD (WW) use license auth
    permission=NO_PERMISSION_REQUIRED,
    renderer = 'json',
    request_method = 'GET'
)
def json_poll_bootstrap(request):
    """
    TODO (WW) share the code with setup.py:json_setup_poll
    """
    eid = request.params['bootstrap_execution_id']
    status, error_message = bootstrap.get_task_status(eid)
    if status == Status.ERROR:
        log.error("can't backup: {}".format(error_message))
        error("Backup couldn't complete. Please try again later. (Error detail: {})"
            .format(error_message))
    else:
        return {'status': status}

@view_config(
    route_name = 'download_backup_file',
    # Since SP is down during backup, we can't authenticate the user
    # (see auth.py:get_principal(). TOOD (WW) use license auth
    permission=NO_PERMISSION_REQUIRED,
    request_method = 'GET'
)
def download_backup_file(request):
    """
    Return the backup file's content as the response.
    Call this method once backup is done (as indicated by json_backup_poll())
    """
    # The brower will use this name as the name for the downloaded file
    name = 'aerofs-backup_{}.tgz'.format(datetime.today().strftime('%Y%m%d-%H%M%S'))
    f = open(_BACKUP_FILE_PATH)
    return Response(content_type='application/x-compressed', app_iter=f,
                    content_disposition='attachment; filename={}'.format(name))