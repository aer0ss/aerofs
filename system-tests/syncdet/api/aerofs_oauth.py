import urlparse
import requests


DEFAULT_CLIENT_ID = "aerofs-ios"
DEFAULT_SECRET = "this-is-not-an-ios-secret"
DEFAULT_REDIRECT_URI = "aerofs://redirect"

class OAuthException(RuntimeError):
    pass


def get_nonce():
    from aerofs_sp import sp as sp_service
    sp = sp_service.connect()
    sp.sign_in()
    return sp.get_access_code()


def get_auth_code(bifrost_url=None,
                  client_id=DEFAULT_CLIENT_ID,
                  redirect_uri=DEFAULT_REDIRECT_URI,
                  nonce=None,
                  state=None,
                  scope="files.read,files.write",
                  verify=True):
    if bifrost_url is None:
        from syncdet.case import local_actor
        bifrost_url = "https://{}/auth".format(local_actor().aero_host)
    if nonce is None:
        nonce = get_nonce()
    r = requests.post(
            bifrost_url+"/authorize",
            data={
                "response_type": "code",
                "client_id": client_id,
                "nonce": nonce,
                "redirect_uri": redirect_uri,
                "state": state,
                "scope": scope},
            allow_redirects=False,
            verify=verify
    )
    if r.status_code != 302:
        raise OAuthException("got http return code {}; expected 302".format(r.status_code))
    if not r.headers["location"].startswith(DEFAULT_REDIRECT_URI+"?"):
        raise OAuthException("server redirected to bad uri: {}".format(r.headers["location"]))
    querys = urlparse.parse_qs(r.headers["location"].split('?', 1)[-1])
    if state is not None and ("state" not in querys or querys["state"][0] != state):
        raise OAuthException("server response state did not match request state")
    if "code" not in querys:
        raise OAuthException("server response did not contain authorization code")
    return querys["code"][0]


def get_access_token(auth_code,
                     bifrost_url=None,
                     client_id=DEFAULT_CLIENT_ID,
                     secret=DEFAULT_SECRET,
                     redirect_uri=DEFAULT_REDIRECT_URI,
                     verify=True):
    if bifrost_url is None:
        from syncdet.case import local_actor
        bifrost_url = "https://{}/auth".format(local_actor().aero_host)
    r = requests.post(
            bifrost_url+"/token",
            auth=(client_id, secret),
            data={
                "grant_type": "authorization_code",
                "code": auth_code,
                "client_id": client_id,
                "redirect_uri": redirect_uri },
            verify=verify
    )
    if r.status_code != 200:
        raise OAuthException("got http return code {}; expected 200".format(r.status_code))
    try:
        return r.json()["access_token"]
    except ValueError, KeyError:
        raise OAuthException("server response did not contain access token")


def revoke_access_token(token,
                        bifrost_url=None,
                        verify=True):
    if bifrost_url is None:
        from syncdet.case import local_actor
        bifrost_url = "https://{}/auth".format(local_actor().aero_host)
    r = requests.delete(bifrost_url+"/token/"+token, verify=verify)
    if r.status_code != 200:
        raise OAuthException("got http return code {}; expected 200".format(r.status_code))
