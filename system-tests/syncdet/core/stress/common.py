"""
common code shared by different stress test
"""

import time
from lib import ritual
from lib.cases.clean_start import clean_start
from syncdet.case.sync import sync


def _stress_harness(test):
    print 'clean start before stress test'
    clean_start()

    start = time.time()

    sync('stress-start')
    test()

    end = time.time()
    print 'total time: {0}s'.format(end - start)

    r = ritual.connect()
    # retrieve the first activity log entry (1 result, starting strictly below index 2)
    first = r.get_activities(1, token=2)[0]
    last = r.get_activities(1)[0]
    print 'activity span: {0}ms'.format(last.time - first.time)


def stress_test(test):
    return lambda: _stress_harness(test)

def stress_spec(**spec):
    if spec.has_key("default"):
        spec["default"] = stress_test(spec["default"])
    if spec.has_key("entries"):
        spec["entries"] = [stress_test(e) for e in spec["entries"]]
    return spec


def wait_no_activity(span=1):
    r = ritual.connect()

    # wait for no activity to happen in a given span
    # TODO: this not an accurate predictor of the core being idle (aliasing for instance may not create activity log...)
    last = r.get_last_activity_index()
    while True:
        time.sleep(span)
        next = r.get_last_activity_index()
        if next == last: break
        last = next
