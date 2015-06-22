import logging
from pyramid.view import view_config
from aerofs_common.bootstrap import BootstrapClient, Status
from web.error import unexpected_error

log = logging.getLogger(__name__)

_URL_PARAM_TASK = 'task'
_URL_PARAM_EXECUTION_ID = 'execution_id'

def get_bootstrap_client(request):
    return BootstrapClient(request.registry.settings["deployment.bootstrap_server_uri"])

@view_config(
    route_name='json_enqueue_bootstrap_task',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def json_enqueue_bootstrap_task(request):
    task = request.params[_URL_PARAM_TASK]
    eid = get_bootstrap_client(request).enqueue_task_set(task)
    log.info("enqueue bootstrap task: {}, eid: {}".format(task, eid))
    return {
        _URL_PARAM_EXECUTION_ID: eid
    }


@view_config(
    route_name='json_get_bootstrap_task_status',
    permission='maintain',
    renderer='json',
    request_method='GET'
)
def json_get_bootstrap_task_status(request):
    """
    TODO (WW) share the code with setup_view.py:json_setup_poll
    """
    eid = request.params[_URL_PARAM_EXECUTION_ID]
    status, error_message = get_bootstrap_client(request).get_task_status(eid)
    if status == Status.ERROR:
        log.error("bootstrap task {} failed: {}".format(eid, error_message))
        unexpected_error("The operation couldn't complete. Please try again later."
              " Error: {}".format(error_message))
    else:
        return {'status': status}
