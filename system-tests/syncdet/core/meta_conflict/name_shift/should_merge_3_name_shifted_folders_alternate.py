"""
The final state of should_merge_3_name_shifted_folders depends on the
ordering of IDs between the creator and swapper.  To ensure that both code
paths are covered, this test executes the same exercise as that other test,
but swapping the creator and swapper devices.
"""
from .should_merge_3_name_shifted_folders import spec

spec['entries'].reverse()
