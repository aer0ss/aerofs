import requests
import json

BOOTSTRAP_BASE_URL = "http://localhost:9070"

def enum(**enums):
    """
    Used to represent enums, until we switch to Python 3.4.
    Reference: http://stackoverflow.com/questions/36932/how-can-i-represent-an-enum-in-python
    """
    return type('Enum', (), enums)

def enqueue_task_set(task_set_name):
    """
    Enqueue a bootstrap task set.
    Throws an exception when server rejects the request to enqueue the task set.

    @return an execution ID. This is used to uniquely identify the task set
    being executed.
    """

    url = BOOTSTRAP_BASE_URL + "/tasksets/" + task_set_name + "/enqueue"
    response = requests.post(url, data={})

    if response.status_code == 200:
        return response.json()[unicode('eid')]
    else:
        raise Exception()

Status = enum(QUEUED="queued", RUNNING="running", ERROR="error", SUCCESS="success")
def get_task_status(execution_id):
    """
    Get the status of a set of tasks being executed by bootstrap. The set of
    tasks is identified by the execution ID.

    @return task status enumeration.
    """
    url = BOOTSTRAP_BASE_URL + "/eids/" + str(execution_id) + "/status"
    response = requests.get(url)

    if response.status_code == 200:
        # TODO (MP) in the future when this call returns more details in the
        # response, we can parse those out as well and return them to the caller
        # to provide the user with more information in the UI.
        return response.json()[unicode('status')]
    else:
        raise Exception()
