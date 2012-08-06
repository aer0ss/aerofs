"""
Exponential retry utility. Used by the services manager to connect to the KVMs
manager and by the KVMs manager to connect to the VM hosts manager.
"""

import time

class ExponentialRetryTimer(object):

    # Max value, 1 minute retry interval.
    CONST_MAX_VALUE = 60.0

    # Multiply by this many each next_time().
    CONST_MULTIPLY = 2.0

    def __init__(self):
        self.reset()

    def reset(self):
        self._timer = 0

    def next_time(self):
        if self._timer == 0:
            self._timer = 0.5
            return 0

        self._timer *= ExponentialRetryTimer.CONST_MULTIPLY
        if self._timer > ExponentialRetryTimer.CONST_MAX_VALUE:
            self._timer = ExponentialRetryTimer.CONST_MAX_VALUE

        return self._timer

"""
Useful when doing exponential retrys.
"""

class AwareSleeper(object):

    def __init__(self):
        self._shutdown = False

    def aware_sleep(self, sleep_time):
        end_time = time.time() + sleep_time
        while time.time() < end_time and self._shutdown == False:
            time.sleep(0.5)
