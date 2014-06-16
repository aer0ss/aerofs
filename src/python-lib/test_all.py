import unittest

import unittests

def test_suite():
    return unittests.test_suite()

# It would be nice if we could use unittest.discover(), but that
# wasn't availble until Python 3.2
if __name__ == "__main__":
    unittest.main(defaultTest='test_suite', testRunner=unittest.TextTestRunner(verbosity=2))
