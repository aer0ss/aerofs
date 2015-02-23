import utils


def main():
    """
    Run the AeroFS App unit tests.
    We run on iPhone only since unit tests should not be dependent on the device type.
    """
    utils.run_test(utils.path_to("App/AeroFS.xcworkspace"), "AeroFSTests_with_args", False,
                   [
                       "AeroFSTests:ConfigurationCodeTest",
                   ])


spec = {'entries': [main]}