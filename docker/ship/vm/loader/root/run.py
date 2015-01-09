from os.path import join
import shutil
from sys import argv
from os import walk
import subprocess
import api
from constants import TAG_PATH
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

if len(argv) == 1:
    print "Starting API service..."
    api.start()

elif argv[1] == 'images':
    for i in loader.get_images():
        print i

elif argv[1] == 'tag':
    print loader.get_tag()

elif argv[1] == 'verify':
    loader.verify()

elif argv[1] == 'load':
    if len(argv) == 3:
        loader.load(argv[2])
    else:
        loader.load()

elif argv[1] == 'install-getty':
    install_getty(argv[2])

elif argv[1] == 'test-getty':
    install_getty('/tmp')
    subprocess.call('/tmp/run')

else:
    raise Exception('Unknown command: {}'.format(argv[1]))
