"""
This test is very similar to
should_detect_changes_with_file_deleted_immediately_after_creation, except that
it tests file movements soon after file creation.
"""

from should_detect_changes_with_file_deleted_immediately_after_creation \
import main

spec = { 'entries': [lambda: main(False)] }
