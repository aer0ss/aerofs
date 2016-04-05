import requests
import json
from Queue import Queue

from util import str2bool, get_deployment_secret

q = Queue()
deployment_secret = None

def analytics_consumer_thread_func():
    global q
    while True:
        f = q.get()
        f()

def send_analytics_event(request, event, get_user_id = True):
    global q, deployment_secret
    if not str2bool(request.registry.settings["analytics.enabled"]):
        return

    data = {
        "event": event
    }
    if get_user_id and request.authenticated_userid is not None:
        data["user_id"] = request.authenticated_userid
    elif get_user_id:
        # we wanted to track user_id, but it was not available
        return

    data["value"] = 1

    if deployment_secret is None:
        deployment_secret = get_deployment_secret(request.registry.settings)
    auth_header_value = 'Aero-Service-Shared-Secret {} {}'.format('web', deployment_secret)
    headers = {'Content-type': 'application/json',
               'Authorization': auth_header_value}
    url = "http://{}:{}{}".format("analytics.service", "9400", "/events")
    q.put(lambda: requests.post(url, data=json.dumps(data), headers=headers))