import os
import errno
import subprocess
from syncdet import case


def run_test(path_to_xcworkspace, scheme, is_ipad, tests_to_run=None):
    """
    Runs iOS tests, and prints the output to stdout.
    Throws CalledProcessError if the tests fails.

    @param path_to_xcworkspace: path to the .xcworkspace file
    @param scheme: scheme name to use.
    @param is_ipad:  True to run on iPad simulator, False to run on iPhone simulator
    @param tests_to_run: list of tests to run. If empty list, runs all tests, otherwise, each item in the list should be
     a string following the syntax of xctool's `-only` command line option:
        'SomeTestTarget'                                 runs all tests in that test target
        'SomeTestTarget:SomeTestClass'                   runs all tests in that class
        'SomeTestTarget:SomeTestClass/testSomeMethod'    runs a specific test method
    @return:
    """
    if not tests_to_run: tests_to_run = []
    os.chdir(os.path.dirname(path_to_xcworkspace))

    args = [
        'xctool',
        '-workspace', os.path.basename(path_to_xcworkspace),
        '-scheme', scheme,
        '-sdk', 'iphonesimulator',
        'test',
        '-simulator', 'ipad' if is_ipad else 'iphone'
    ]
    if tests_to_run: args.extend(['-only', ','.join(tests_to_run)])

    print run_process(args)


def run_process(args):
    """
    Runs a process and waits until it finishes.
    If the process returns a non-zero exit code, an explicit error message is printed to the console, and the original
    CalledProcessError exception is re-thrown

    @param args: list of arguments. E.g.: ['ls', '-la']
    @return: the output of the process. (stdout + stderr)
    """
    try:
        return subprocess.check_output(args, stderr=subprocess.STDOUT)
    except subprocess.CalledProcessError as e:
        print "\n######## Command failed! ########\n" \
              "    `{}` returned {}\n" \
              "    {}\n" \
              "#################################\n\n".format(' '.join(e.cmd), e.returncode, e.output)
        raise


def path_to(path):
    """ Helper function to return a path inside the ios src folder """
    return os.path.join(case.deployment_folder_path(), "repos/aerofs-ios", path)


def makedirs(path):
    """
    Utility function to make directories recursively if they don't exist.
    Similar to `mkdir -p`
    """
    try:
        os.makedirs(path)
    except OSError as e:
        if e.errno == errno.EEXIST and os.path.isdir(path):
            pass
        else: raise