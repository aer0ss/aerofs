"""
The final state of should_merge_name_swapped_folders depends on the ordering of
device IDs between the creator and swapper.  To ensure that both code paths are
covered, this test executes the same exercise as that other test, but swapping
the creator and swapper devices.
"""
from .should_merge_name_swapped_folders import spec

spec['entries'].reverse()
