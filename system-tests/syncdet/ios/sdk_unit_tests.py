import utils


def main():
    """
    Run the AeroFS SDK unit tests.
    We run on iPhone only since unit tests should not be dependent on the device type.
    """
    utils.run_test(utils.path_to("SDK/AeroFSSDK.xcworkspace"), "AeroFSSDKTests_with_args", False,
                   [
                       "AeroFSSDKTests:AROJavaPropertiesParserTest",
                       "AeroFSSDKTests:AROKeychainWrapperTest",
                       "AeroFSSDKTests:AROSecUtilTest",
                   ])


spec = {'entries': [main]}