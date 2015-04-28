from os.path import join
import shutil
from sys import argv, stderr
from os import walk
from os.path import isfile
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
    if isfile('/banner'):
        shutil.copy2('/banner', join(dest, 'banner'))


def main():
    if len(argv) == 1:
        raise Exception('Please specify a command')

    elif argv[1] == 'images':
        for i in loader.get_images():
            print i

    elif argv[1] == 'tag':
        print loader.get_tag()

    elif argv[1] == 'verify':
        loader.verify(argv[2])

    elif argv[1] == 'load':
        if len(argv) != 5:
            print >>stderr, "Usage: {} {} repo_file tag_file target_file".format(argv[0], argv[1])
            print >>stderr, "       Provide an empty repo file to use the default Docker repo."
            print >>stderr, "       Provide an empty target file to launch the default Crane target."
            print >>stderr, "       Provide an empty tag file to use the 'latest' tag and override " \
                            "the Loader's own tag file. Otherwise content of the two files must be identical."
            raise Exception('Wrong arguments for command {}'.format(argv[1]))
        loader.load(argv[2], argv[3], argv[4])

    elif argv[1] == 'install-getty':
        install_getty(argv[2])

    elif argv[1] == 'modified-yml':
        if len(argv) != 4:
            print >>stderr, "Usage: {} {} <repo> <tag>".format(argv[0], argv[1])
            exit(11)
        loader.modify_yaml(argv[2], argv[3])
        with open(MODIFIED_YML_PATH) as f:
            print f.read()

    elif argv[1] == 'simulate-getty':
        install_getty('/tmp')
        subprocess.call('/tmp/run')

    else:
        raise Exception('Unknown command: {}'.format(argv[1]))


main()