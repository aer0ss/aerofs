import requests

def enum(**enums):
    """
    Used to represent enums, until we switch to Python 3.4.
    Reference: http://stackoverflow.com/questions/36932/how-can-i-represent-an-enum-in-python
    """
    return type('Enum', (), enums)

# See the example code below for usage of this enum
Status = enum(QUEUED="queued", RUNNING="running", ERROR="error", SUCCESS="success")

class BootstrapClient(object):
    def __init__(self, base_url):
        self.base_url = base_url

    def enqueue_task_set(self, task_set_name):
        """
        Enqueue a bootstrap task set.
        Throws an exception when server rejects the request to enqueue the task set.

        @return an execution ID. This is used to uniquely identify the task set
        being executed.
        """

        url = self.base_url + "/tasksets/" + task_set_name + "/enqueue"
        response = requests.post(url, data={})

        if response.status_code == 200:
            return response.json()[unicode('eid')]
        else:
            raise Exception('bootstrap.enqueue_task failed with status {}'
                .format(response.status_code))


    def get_task_status(self, execution_id):
        """
        Get the status of a set of tasks being executed by bootstrap. The set of
        tasks is identified by the execution ID.

        @return (task status enumeration, error message) the error message is None
        if not present in the rboostrap reply

        Example:

        status, error_message = bootstrap.get_task_status(eid)
        if status == Status.ERROR:
            log.error(error_message)
        elif status == Status.SUCCESS:
            log.info("complete!")
        """

        url = self.base_url + "/eids/" + str(execution_id) + "/status"
        response = requests.get(url)

        if response.status_code == 200:
            # TODO (MP) in the future when this call returns more details in the
            # response, we can parse those out as well and return them to the caller
            # to provide the user with more information in the UI.
            json = response.json()
            # Have to use get() rather than json[] for error_message since the field
            # is optinal.
            return json[unicode('status')], json.get(unicode('error_message'))
        else:
            raise Exception()
