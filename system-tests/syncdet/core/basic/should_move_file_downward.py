
from should_move_file import in_up, in_down, test_spec

spec = test_spec(in_up("test_file"), in_down("test_moved"))
