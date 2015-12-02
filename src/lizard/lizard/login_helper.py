from flask import current_app, session
import flask_login as login


def _get_store():
    return current_app.kvsession_store


_EMAIL_SID_SEPARATOR = '_'
def _get_marker_key(email, sid):
    return email + _EMAIL_SID_SEPARATOR + sid


def login_user(admin):
    """
    Creates the session for a given user.
    :return: True if logged in successfully, False otherwise.
    """
    login_success = login.login_user(admin)
    if not login_success:
        return False
    store = _get_store()
    ttl = current_app.permanent_session_lifetime.total_seconds()
    store.put(_get_marker_key(admin.email, session.sid_s), session.sid_s, ttl)
    return True


def _delete_session(store, email, sid):
    store.delete(sid)
    store.delete(_get_marker_key(email, sid))

def logout_user_this_session():
    """
    Destroy the session for the user currently logged in.
    """
    store = _get_store()
    _delete_session(store, login.current_user.email, session.sid_s)
    login.logout_user()


def logout_user_all_sessions(admin):
    """
    Destroy all sessions belonging the given user.
    """
    store = _get_store()
    for marker in store.redis.keys(admin.email + _EMAIL_SID_SEPARATOR + "*"):
        _delete_session(store, admin.email, store.get(marker))
