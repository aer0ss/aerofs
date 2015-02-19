from os.path import join
import shutil
from sys import argv, stderr
from os import walk
import subprocess
import api
from common import TAG_PATH, MODIFIED_YML_PATH
import loader


def install_getty(dest):
    src = '/getty'
    # Copy /getty. Can't use copytree(src, dest) as it assumes dest doesn't exist.
    for root, dirs, files in walk(src):
        for d in dirs:
            shutil.copytree(join(src, d), join(dest, d))
        for f in files:
            shutil.copy2(join(src, f), join(dest, f))
        break

    # Copy /tag & /banner
    shutil.copy2(TAG_PATH, join(dest, 'tag'))
    shutil.copy2('/banner', join(dest, 'banner'))


def main():
    if len(argv) == 1:
        # Simulate the API by default
        undefined = '(undefined - for testing purposes, use the Loader\'s simulate-api command to define it)'
        api.start(undefined, undefined, '/dev/null', '/dev/null', loader.get_tag())

    elif argv[1] == 'images':
        for i in loader.get_images():
            print i

    elif argv[1] == 'tag':
        print loader.get_tag()

    elif argv[1] == 'verify':
        loader.verify()

    elif argv[1] == 'load':
        if len(argv) != 4:
            print >>stderr, "Usage: {} {} repo_file target_file".format(argv[0], argv[1])
            print >>stderr, "       Provide an empty repo file to use the default Docker repo."
            print >>stderr, "       Provide an empty target file to launch the default crane target."
            raise Exception('Wrong arguments for command {}'.format(argv[1]))
        loader.load(argv[2], argv[3])

    elif argv[1] == 'install-getty':
        install_getty(argv[2])

    elif argv[1] == 'modified-yaml':
        if len(argv) != 3:
            print >>stderr, "Usage: {} {} repo".format(argv[0], argv[1])
            exit(11)
        loader.modify_yaml(argv[2], None)
        with open(MODIFIED_YML_PATH) as f:
            print f.read()

    elif argv[1] == 'simulate-getty':
        install_getty('/tmp')
        subprocess.call('/tmp/run')

    elif argv[1] == 'simulate-api':
        loader.simulate_api(argv[2], argv[3])

    else:
        raise Exception('Unknown command: {}'.format(argv[1]))


main()