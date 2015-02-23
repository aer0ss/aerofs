"""
Exercise scan of a large directory hierarchy with many small files

Use mixed scan: i.e files are created when the daemon is running
"""

import common
from stress_scanner_small_files_pure import create

spec = common.stress_spec(entries=[(lambda: create(False))])
