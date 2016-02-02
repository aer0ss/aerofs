from api import aerofs_oauth
from aerofs_sp import sp as sp_service


def get_oauth_token_for_user(user):
    sp = sp_service.connect()
    sp.sign_in(actor=user)
    auth_code = aerofs_oauth.get_auth_code(
            nonce=sp.get_access_code(),
            state="Hello, everybody? Hello, everybody!",
            verify=False
    )
    print 'auth code for user {} is {}'.format(user.aero_userid, auth_code)
    # get a token
    token = aerofs_oauth.get_access_token(auth_code, verify=False)
    print 'token for user {} is {}'.format(user.aero_userid, token)
    return token
