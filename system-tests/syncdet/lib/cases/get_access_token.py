
import requests
from aerofs_sp import sp as sp_client
from syncdet.case import local_actor

def main():
    sp = sp_client.connect()
    sp.sign_in()
    code = sp.get_mobile_access_code()
    print('access_code: {}'.format(code))

    r = requests.post('https://' + local_actor().aero_host + '/auth/token', {
        'client_id': 'aerofs-ios',
        'client_secret': 'this-is-not-an-ios-secret',
        'grant_type': 'authorization_code',
        'code_type': 'device_authorization',
        'code': code
    }, verify=False)

    print('response: {}'.format(r.text))


spec = {'entries': [main]}
