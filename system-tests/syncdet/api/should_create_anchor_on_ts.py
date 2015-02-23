# IMPORTANT - Conditions for running this test:
#   -   The first actor needs to be a team server
#   -   The second actor does not have to be the same user as the first, but must
#   be in the same organization

import json

import requests
from syncdet.actors import actor_list
from syncdet.case import instance_unique_string, local_actor

import aerofs_oauth
from aerofs_common.convert import store_relative_to_pbpath
from aerofs_ritual.id import get_root_sid_bytes
from lib import ritual
from lib.files import wait_dir, instance_unique_path


def ts():
    sid = get_root_sid_bytes(actor_list()[1].aero_userid)
    pbpath = store_relative_to_pbpath(sid, instance_unique_string())
    print 'wait for anchor'
    ritual.connect().wait_pbpath(pbpath)


def sharer():
    # authorize the app -- note that we could just get a token with the nonce,
    # but we are emulating the user flow where the app is not given credentials;
    # only an authorization code that can be traded for a token
    auth_code = aerofs_oauth.get_auth_code(
            state="OnePlopIsWorthAThousandDrop",
            scope="files.read,files.write,acl.read,acl.write",
            verify=False
    )
    print 'auth code is', auth_code

    # get a token
    token = aerofs_oauth.get_access_token(auth_code, verify=False)
    print 'token is', token 

    # create a new shared folder
    print 'creating shared folder through API...'
    r = requests.post("https://{}/api/v1.1/shares".format(local_actor().aero_host),
                      data=json.dumps({"name": instance_unique_string()}),
                      headers={
                          "Authorization": "Bearer " + token,
                          'Content-Type': 'application/json'
                      })
    r.raise_for_status()
    print r.json()

    wait_dir(instance_unique_path())


spec = {"entries": [ts, sharer]}
