import unittests, unittest

suite = unittest.TestSuite()
for mod in unittests.__all__:
    suite.addTest(unittest.TestLoader().loadTestsFromModule(mod))

unittest.TextTestRunner().run(suite)