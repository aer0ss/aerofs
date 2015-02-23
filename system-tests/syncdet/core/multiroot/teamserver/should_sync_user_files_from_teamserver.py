# IMPORTANT - Conditions for running this test:
#   -   The first actor needs to be a team server configured to use linked storage
#   -   The second actor does not have to be the same user as the first, but must
#   be in the same organization

from .should_sync_user_files_to_teamserver import *

spec = ts_spec(teamserver=(put, ts_path), clients=[(get, client_path)])
