import os.path
import requests
import aerofs_oauth
from syncdet.case import local_actor
from lib.files import instance_unique_path


def main():
    API_URL = "https://{}/api/v1.2".format(local_actor().aero_host)

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

    # list contents of root anchor
    # IMPORTANT: request the optional path field to catch regression of root resolution on block storage
    print 'calling api...'
    r = requests.get(API_URL+"/folders/root",
                     headers={"Authorization": "Bearer " + token},
                     params={"fields": "path,children"})
    r.raise_for_status()
    print r.json()

    j = r.json()
    assert j["name"] == "AeroFS"
    assert not j["is_shared"]
    assert j["parent"] == j["id"]
    assert len(j["path"]["folders"]) == 0

spec = {"entries": [main]}
