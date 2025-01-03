"""
NB: this test assumes a single user but admits any mixture of regular clients and TS

"""
import time
import binascii

import requests
from syncdet.case import actor_count, actor_id, local_actor, instance_unique_hash32, instance_unique_string
from syncdet.case.sync import sync

import aerofs_oauth
from aerofs_common.convert import store_relative_to_pbpath
from aerofs_common.param import POLLING_INTERVAL
from aerofs_ritual import id
from lib import ritual
from lib.app.aerofs_proc import stop_all, run_ui


CONTENT = "hello world"


def _is_lucky():
    luck = instance_unique_hash32() % actor_count()
    return luck == actor_id()


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

    sid = id.get_root_sid_bytes(local_actor().aero_userid)
    pbpath = store_relative_to_pbpath(sid, instance_unique_string())

    # create a file in the root anchor
    # NB: use Ritual for TS-friendliness
    r = ritual.connect()
    if _is_lucky():
        print 'create file'
        r.write_pbpath(pbpath, CONTENT)

    # wait for file to sync on all actors
    # NB: use Ritual for TS-friendliness
    r.wait_pbpath_with_content(pbpath, CONTENT)
    oid = binascii.hexlify(r.test_get_pbpath_identifier(pbpath).oid)
    resource = "/files/" + binascii.hexlify(sid) + oid + "/content"
    print "url: {}".format(resource)

    sync("synced")

    # stop AeroFS on all actors
    stop_all()
    sync("stopped")

    # use a session to test fallback when a cookie points to a stale route
    s = requests.Session()
    s.headers["Authorization"] = "Bearer " + token

    for i in range(0, actor_count()):
        # start AeroFS on a single actor
        if actor_id() == i:
            run_ui()
        sync("start-{}".format(i))

        # get file contents (only request that actually goes to daemon post-Phoenix)
        print 'calling api...'
        n = 0
        # the daemon may take a little while to establish a connection to the gateway
        # keep retrying for up to 3s when getting either
        #     503 Service Unavailable
        #     504 Gateway Timeout
        while True:
            r = s.get(API_URL+resource)
            if (r.status_code != 503 and r.status_code != 504 and r.status_code != 404) or n == 15:
                print '{}: {}'.format(r.status_code, r.text)
                r.raise_for_status()
                c = requests.utils.dict_from_cookiejar(r.cookies)
                print 'from {}'.format(c['route'])
                if r.text == CONTENT:
                    break
            time.sleep(2 * POLLING_INTERVAL)
            n += 1

        sync("stop-{}".format(i))
        if actor_id() == i:
            stop_all()

    run_ui()
    sync("started")


spec = {"default": main}
