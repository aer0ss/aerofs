import requests

from web.util import get_rpc_stub


def get_new_oauth_token(request, client_id, client_secret, expires_in=0):
    # N.B. (JG) the get_mobile_access_code RPC returns a proof-of-identity nonce that
    # Bifrost uses for authentication. It was originally designed for a mobile app
    # and its original name remains to maintain backwards compatibility
    r = requests.post(request.registry.settings["deployment.oauth_server_uri"]+'/token',
        data={
            'grant_type': 'authorization_code',
            'code': get_rpc_stub(request).get_mobile_access_code().accessCode,
            'code_type': 'device_authorization',
            'client_id': client_id,
            'client_secret': client_secret,
            'expires_in': expires_in,
        }
    )
    r.raise_for_status()
    token = r.json()['access_token']
    return token


def delete_oauth_token(request, token):
    r = requests.delete(request.registry.settings["deployment.oauth_server_uri"]+'/token/'+token)
    if r.status_code == 404:
        return
    r.raise_for_status()

