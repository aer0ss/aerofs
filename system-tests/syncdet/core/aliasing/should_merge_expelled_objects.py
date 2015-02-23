"""
Aliasing should work even if both objects are expelled.
"""

from should_merge_expelled_and_admitted_objects import creator1, creator2, receiver

spec = { 'entries': [lambda: creator1(True), lambda: creator2(True)],
         'default': receiver }