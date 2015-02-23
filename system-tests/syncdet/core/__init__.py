

from syncdet.case import local_actor, actor_count
from syncdet.case.sync import sync_ng


def is_polaris():
    try:
        return 'polaris' in local_actor().aero_flags
    except (AttributeError, TypeError):
        return False


def wait_aliased(id, r, p, count=None):
    if count is None:
        count = actor_count()

    if is_polaris():
        sync_ng(id,
                validator=lambda votes: sum(votes.itervalues()) == count - 1,
                vote=lambda: 1 if len(r.test_get_alias_object(p)) >= 1 else None)
    else:
        r.test_wait_aliased(p, count - 1)
