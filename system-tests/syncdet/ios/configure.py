import utils
import os
from syncdet import case
from string import Template


def main():
    configure_tests(utils.path_to("App/AeroFS.xcodeproj"), "AeroFSTests", "AeroFSTests_with_args")
    configure_tests(utils.path_to("SDK/AeroFSSDK.xcodeproj"), "AeroFSSDKTests", "AeroFSSDKTests_with_args")


def configure_tests(path_to_xcodeproj, input_scheme_name, output_scheme_name):
    """
    Configure the tests so that they are ready to run. This involves two steps:

    1. Setting up the command line arguments in the scheme
    2. Running 'pod install' to install the dependencies

    'schemes' are XML files that define how Xcode should build and run a target. As far as I can tell, schemes are the
    only way run an iOS app with command line arguments. We use command line arguments to pass the local prod URL as
    well as the user id and password from sycdet to the iOS tests.

    @param path_to_xcodeproj: full path to the .xcodeproj file
    @param input_scheme_name: name of the scheme we want to use as a template to set the arguments
    @param output_scheme_name: name of the new scheme that will be generated from the template.
    """

    # Read the content from the input scheme file
    with open(scheme_path(path_to_xcodeproj, input_scheme_name, True), 'r') as f:
        content = f.read()

    # Replace the variables in the file with their values
    content = Template(content).substitute({
        'WEB_URL': "https://" + case.local_actor().aero_host,
        'USER_ID': case.local_actor().aero_userid,
        'PASSWORD': case.local_actor().aero_password,
    })

    # Save the content as a new scheme under the user's private schemes directory
    output_path = scheme_path(path_to_xcodeproj, output_scheme_name, False)
    utils.makedirs(os.path.dirname(output_path))
    with open(output_path, 'w') as f:
        f.write(content)

    # Run 'pod install'
    os.chdir(os.path.dirname(path_to_xcodeproj))
    utils.run_process(['pod', 'install'])


def scheme_path(path_to_xcodeproj, scheme_name, is_shared):
    """
    Returns the path to a Xcode scheme file.

    @param path_to_xcodeproj: path to the .xcodeproj file
    @param scheme_name: name of the scheme (e.g. "My Scheme")
    @param is_shared: whether the scheme should be in the shared dir (under source control) or under the user-specific
    dir (gitignored).
    """
    return os.path.join(
        path_to_xcodeproj,
        "xcshareddata" if is_shared else "xcuserdata/{}.xcuserdatad".format(os.environ['USER']),
        "xcschemes",
        scheme_name + ".xcscheme"
    )


spec = {'entries': [main]}
