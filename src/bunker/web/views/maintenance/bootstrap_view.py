import logging
from pyramid.view import view_config
from aerofs_common import bootstrap
from aerofs_common.bootstrap import Status
from web.util import error

log = logging.getLogger(__name__)

_URL_PARAM_TASK = 'task'
_URL_PARAM_EXECUTION_ID = 'execution_id'


@view_config(
    route_name='json_enqueue_bootstrap_task',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def json_enqueue_bootstrap_task(request):
    task = request.params[_URL_PARAM_TASK]
    eid = bootstrap.enqueue_task_set(task)
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
    status, error_message = bootstrap.get_task_status(eid)
    if status == Status.ERROR:
        log.error("bootstrap task {} failed: {}".format(eid, error_message))
        error("The operation couldn't complete. Please try again later."
              " Error: {}".format(error_message))
    else:
        return {'status': status}
