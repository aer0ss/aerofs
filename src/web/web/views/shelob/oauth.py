import requests

from web.util import get_rpc_stub


def get_new_oauth_token(request):
    # N.B. (JG) the get_mobile_access_code RPC returns a proof-of-identity nonce that
    # Bifrost uses for authentication. It was originally designed for a mobile app
    # and its original name remains to maintain backwards compatibility
    r = requests.post(request.registry.settings["deployment.oauth_server_uri"]+'/token',
        data={
            'grant_type': 'authorization_code',
            'code': get_rpc_stub(request).get_mobile_access_code().accessCode,
            'code_type': 'device_authorization',
            'client_id': 'aerofs-shelob',
            'client_secret': request.registry.settings["oauth.shelob_client_secret"],
        }
    )
    r.raise_for_status()
    token = r.json()['access_token']
    return token
