import requests
import aerofs_oauth
from syncdet.case import local_actor


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

    # revoke token
    aerofs_oauth.revoke_access_token(token, verify=False)

    # api call should fail
    print 'calling api with revoked token...'
    r = requests.get(API_URL+"/children/", headers={"Authorization": "Bearer " + token})
    assert r.status_code == 401, r


spec = {"entries": [main]}
