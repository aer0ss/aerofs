"""
Tests that the client can reach SV by sending a defect report.
"""

from lib import ritual

def send_defect():
    r = ritual.connect()

    # If this blocking method call does not raise an Exception,
    # then the call succeeded.
    r.test_log_send_defect()

spec = { "entries": [send_defect] }
