from flask import current_app

KEY_PREFIX = 'lizard-pw-reset_'


def _get_store():
    return current_app.kvsession_store


def use(password_reset_token):
    _get_store().put(KEY_PREFIX + password_reset_token, '', 60*60)


def has_been_used(password_reset_token):
    try:
        _get_store().get(KEY_PREFIX + password_reset_token)
        return True
    except KeyError:
        return False
