import aerofs_oauth
import requests

from lib.app import cfg, aerofs_proc
from syncdet.case import local_actor
from syncdet.case.assertion import assertEqual, fail


# Notes: this system test has to run on Linux because this test uses the
# shell client which relies on JLine which is not supported nor functional
# on Windows and OS X.
#
# This system test assumes there are no other devices using the same account.
# Thus, it must be run in an one-client setup.
#
# This system test is not 100% reliable. It relies on the rest service to
# connect to the rest api gateway before the daemon responds to the heart beat.
#
# In the case of verify_disabled, it is possible to get false positive. The
# rest service is not online yet but may be online later.
#
# In the case of verify_enabled, it is possible to get false negative. The
# rest service is on its way but is not online yet.
#
# If this test fails and it look illegimate, you have my (Alex Tsay)'s
# permission to remove this test. (JGray made me write this :( )
def main():
    set_value(False)
    verify_value(False)
    verify_disabled()

    # it's important to test with api access enabled _last_ because other
    # system tests may assume that api access is enabled by default
    set_value(True)
    verify_value(True)
    verify_enabled()


def verify_enabled():
    r = make_request()

    # ensure success
    r.raise_for_status()

    # verify that this device is indeed online
    assertEqual(cfg.get_cfg().did().get_hex(), r.cookies['route'])


def verify_disabled():
    r = make_request()

    # 503 => all clients offline, which means success
    if r.status_code == 503:
        return

    # fail at this point since checking for cookies isn't conclusive anyway
    fail()


def set_value(enabled):
    # set the state using the shell client
    value = 'enabled' if enabled else 'disabled'
    aerofs_proc.run_sh_and_check_output('config', 'api', value)

    # restart #allthethings
    aerofs_proc.stop_all()

    # run_ui implicitly waits for the daemon heart beat.
    #
    # Note that there's no way to reliably wait for the rest service to come
    # online. The best we can do is to wait for the daemon heart beat and hope
    # that the rest service will be online by now if it's coming online.
    #
    # This is a race condition, and we cannot guarantee that the outcome is as
    # desired. Nevertheless, we assume the rest service will be online by now
    # if it's coming online.
    aerofs_proc.run_ui()


def verify_value(enabled):
    # query the state using the shell client
    output = aerofs_proc.run_sh_and_check_output('config', 'api')

    # search for the expected log line
    expected = 'api: {}'.format('enabled' if enabled else 'disabled')
    if not any(expected == line.strip() for line in output.splitlines()):
        # the expected log line is not found
        fail()


def make_request():
    auth_code = aerofs_oauth.get_auth_code(
        state="Hello, everybody? Hello, everybody!",
        verify=False)
    print 'code {}'.format(auth_code)
    token = aerofs_oauth.get_access_token(auth_code, verify=False)
    print 'token {}'.format(token)
    url = "https://{}/api/v0.9/children/".format(local_actor().aero_host)
    headers = {"Authorization": "Bearer " + token,
               "Route": cfg.get_cfg().did().get_hex()}

    return requests.get(url, headers=headers)

spec = {'entries': [main]}
