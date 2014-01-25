import unittest

import unittests

def test_suite():
    return unittests.test_suite()

if __name__ == "__main__":
    unittest.main(defaultTest='test_suite', testRunner=unittest.TextTestRunner(verbosity=2))
