import os.path
import requests
import time
import aerofs_oauth
from aerofs_common import param
from syncdet.case import local_actor
from lib.files import instance_unique_path


MAX_ATTEMPTS=10


def main():
    API_URL = "https://{}/api/v0.9".format(local_actor().aero_host)

    # authorize the app -- note that we could just get a token with the nonce,
    # but we are emulating the user flow where the app is not given credentials;
    # only an authorization code that can be traded for a token
    auth_code = aerofs_oauth.get_auth_code(
            state="Hello, everybody? Hello, everybody!",
            verify=False
    )
    print 'auth code is', auth_code

    # get a token
    token = aerofs_oauth.get_access_token(auth_code, verify=False)
    print 'token is', token

    # create a file in the root anchor
    filepath = instance_unique_path()
    filename = os.path.basename(filepath)
    with open(instance_unique_path(), 'w') as f:
        f.write("hello world")

    # list contents of root anchor
    print 'calling api...'
    attempts = 0
    while attempts < MAX_ATTEMPTS:
        r = requests.get(API_URL+"/children/", headers={"Authorization": "Bearer " + token})
        r.raise_for_status()
        print r.json()

        # find file in list
        if any(f["name"] == filename for f in r.json()["files"]):
            break
        time.sleep(param.POLLING_INTERVAL)
        attempts += 1

spec = {"entries": [main]}
