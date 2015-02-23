import errno
import os
import random
import string
import sys
import time

import syncdet.case
from syncdet.case.assertion import assertTrue, assertEqual

from aerofs_common import param
from lib import ritual
from ..app.cfg import get_cfg


_MAX_DIRECTORY_INDEX = 100000000
_NUM_RAND_BITS = 31
_FILENAME_LEN = 30
_WIN32_DISALLOWED_TRAILING_CHARACTERS = ' .'  # disallow spaces and dots


def init(seed):
    """
    @param seed        the seed to be used for all random calls
    """
    random.seed(seed)


def instance_unique_path():
    """
    @return a path rooted in the AeroFS anchor, that is unique for each instance
    of a test case execution
    """
    return os.path.join(get_cfg().get_root_anchor(),
                        syncdet.case.instance_unique_string())


def instance_path(*name):
    return os.path.join(instance_unique_path().encode('utf-8'), *name)


def _wait_path(path):
    """
    Wait for a single file or directory at the given path to appear.
    """
    while not os.path.exists(path):
        time.sleep(param.POLLING_INTERVAL)


def _wait_path_modified(path, base):
    """
    Wait for a single file to be modified (based on filesize and mtime)
    If the file disappears, wait for it to re-appear

    @param path The path of the file to wait to appear or to show a change
    @param base an os.stat() structure for path before this function was called,
                so we can compare size/mtime without race conditions
    """
    try:
        last_stat = os.stat(path)
        while last_stat.st_mtime == base.st_mtime and last_stat.st_size == base.st_size:
            time.sleep(param.POLLING_INTERVAL)
            last_stat = os.stat(path)
    except EnvironmentError as err:
        # handle the file disappearing
        # ENOENT : no such file or directory
        if err.errno != errno.ENOENT:
            raise
        _wait_path(path)


def wait_dir(path):
    """
    Wait for a single directory at the given path by polling
    """
    _wait_path(path)
    assertTrue(os.path.isdir(path), path + ' must be a directory')


def wait_file(path, size=None):
    """
    Wait for a single file (not directory) at the given path by polling
    """
    _wait_path(path)
    assertTrue(os.path.isfile(path), path + ' must be a file')
    if size is not None:
        assertEqual(size, os.path.getsize(path))


def wait_file_with_content(path, content, base=None):
    """
    Wait for a single file to appear at the given path with the given content
    If a base timestamp is provided, wait for the timestamp to change

    NB: will fail the test if the file appears with mismatching content

    @param path        absolute path of file (assumes ~ is expanded)
    @param content     must be one line only
    @param base        os.stat() of the last observed version of the file, None by default
    """
    assert content
    _wait_file_with_content(path, (lambda line: _accept_content(content, line)), base)


def _accept_content(expected, actual):
    # On Windows, to inherit DACLs from the parent folder when a file is first downloaded,
    # the prefix is not immediately moved to the target location. Instead a dummy empty
    # file is created and then ReplaceFile is used to transfer DACLs to the prefix before
    # moving it to the target location. This requires a small adjustment to tests to prevent
    # race conditions from triggering an assert failure
    if len(actual) == 0 and len(expected) != 0:
        return False
    assertEqual(expected, actual)
    return True


def wait_file_new_content(path, content, base=None):
    """
    Wait for a single file to appear at the given path with the given content
    If a base timestamp is provided, wait for the timestamp to change

    NB: will keep polling if the file appears with mismatching content

    @param path        absolute path of file (assumes ~ is expanded)
    @param content     must be one line only
    @param base        os.stat() of the last observed version of the file, None by default
    """
    assert content
    _wait_file_with_content(path, (lambda line: content == line), base)


def _wait_file_with_content(path, accept_content, base=None):
    """
    Wait for a single file to appear at the given path with a matching content
    If a base timestamp is provided, wait for the timestamp to change

    NB: will keep polling if the file appears with mismatching content

    @param path             absolute path of file (assumes ~ is expanded)
    @param accept_content   boolean function taking the first line of the file as argument
    @param base             os.stat() of the last observed version of the file, None by default
    """
    while True:
        if (base is not None) and os.path.exists(path):
            _wait_path_modified(path, base)
        else:
            _wait_path(path)

        try:
            f = open(path, 'rb')
        except EnvironmentError as err:
            # handle the file disappearing after the conditional above, but
            # before opening the file
            # ENOENT : no such file or directory
            # EACCES : on windows create/delete race result in Access Denied instead of Not Found
            if err.errno != errno.ENOENT:
                if not (err.errno == errno.EACCES and "win32" in sys.platform):
                    raise
        else:
            with f:
                line = f.readline()
            if accept_content(line):
                break
            time.sleep(param.POLLING_INTERVAL)


def wait_file_in_sync(path, content, base=None):
    """
    Wait for a single file to become synced with at least one peer. See above method (wait file
    with content) for param descriptions.
    """

    wait_file_with_content(path, content, base)
    r = ritual.connect()

    # At this point the file has been updated on the system. Wait for the syncstat infra to update
    # the status to synced.
    while True:
        if r.is_synced(path):
            break

        time.sleep(param.POLLING_INTERVAL)


def wait_dir_in_sync(path):
    """
    Wait for a single directory to be reported as synced by the syncstat infrastructure.
    """

    wait_dir(path)
    r = ritual.connect()

    while not r.is_synced(path):
        time.sleep(param.POLLING_INTERVAL)


def wait_dir_tree_in_sync(path):
    _wait_dir_tree_in_sync(path, ritual.connect())


def _wait_dir_tree_in_sync(path, r):
    if os.path.isdir(path):
        for sub in os.listdir(path):
            _wait_dir_tree_in_sync(os.path.join(path, sub), r)

    while not r.is_synced(path):
        time.sleep(param.POLLING_INTERVAL)


def wait_dir_tree_scanned(root, depth, num_subdirs, num_files, r):
    """
    Wait for a non-random directory tree created by make_dir_tree to be scanned
    @param root          the directory containing the tree to be waited on
    @param depth         the depth of the directory tree. For example,
                         0: make files in root, with no subdirectories
                         1: make files in root, with one more level of subdirs
    @param num_subdirs   number of subdirectories for each directory
    @param num_files     number of files per directory
    @param max_file_size maximum file size, in bytes
    """

    wait_fn = lambda path: r.wait_path(path)
    _wait_dir_tree(root, depth, num_subdirs, num_files,
                   wait_fn, wait_fn)


def wait_dir_tree(root, depth, num_subdirs, num_files, max_file_size):
    """
    Wait for a non-random directory tree created by make_dir_tree to appear
    @param root          the directory containing the tree to be waited on
    @param depth         the depth of the directory tree. For example,
                         0: make files in root, with no subdirectories
                         1: make files in root, with one more level of subdirs
    @param num_subdirs   number of subdirectories for each directory
    @param num_files     number of files per directory
    @param max_file_size maximum file size, in bytes
    """
    _wait_dir_tree(root, depth, num_subdirs, num_files,
                   wait_dir, (lambda path: wait_file(path, max_file_size)))


def _wait_dir_tree(root, depth, num_subdirs, num_files, wait_dir_fn, wait_file_fn):
    assert depth >= 0

    #print 'wait: ', root
    wait_dir_fn(root)

    for i in xrange(0, num_files):
        wait_file_fn(os.path.join(root, '{0}'.format(i)))

    if depth > 0:
        for i in xrange(0, num_subdirs):
            dir_path = os.path.join(root, 'd_{0}'.format(i))
            _wait_dir_tree(dir_path, depth - 1, num_subdirs,
                           num_files, wait_dir_fn, wait_file_fn)


def make_dir_tree(root, depth, num_subdirs, num_files, max_file_size,
                  randomize=False, create_root=True):
    """
    @param root          the directory containing the tree to be created
    @param depth         the depth of the directory tree. For example,
                         0: make files in root, with no subdirectories
                         1: make files in root, with one more level of subdirs
    @param num_subdirs   number of subdirectories for each directory
    @param num_files     number of files per directory
    @param max_file_size maximum file size, in bytes
    @param randomize     randomize the file names, sizes and num subdirs and files, etc
    @param create_root   True to create the root folder. If False, the folder
                         must exist.
    """

    assert depth >= 0

    if create_root:
        os.makedirs(root)
    else:
        assert os.path.exists(root)

    for i in xrange(0, num_files):
        # Create a filename, determine a file size, then write content to file
        file_name = get_rand_filename() if randomize else '{0}'.format(i)
        file_path = os.path.join(root, file_name)
        if os.path.exists(file_path):
            print 'Warning: file {0} is being overwritten.'.format(file_path)
        file_size = random.randint(0, max_file_size) if randomize else max_file_size
        if randomize:
            write_file(file_path, file_size)
        else:
            with open(file_path, 'w') as f:
                f.write(''.join(['x' for _ in range(max_file_size)]))

    if depth > 0:
        for i in xrange(0, num_subdirs):
            dir_name = get_rand_dirname('d_') if randomize else 'd_{0}'.format(i)
            dir_path = os.path.join(root, dir_name)
            os.mkdir(dir_path)
            make_dir_tree(dir_path, depth - 1, num_subdirs,
                          num_files, max_file_size, randomize, False)


def get_rand_dirname(prefix):
    return '{0}{1}'.format(prefix, random.getrandbits(_NUM_RAND_BITS))


# TODO: make the sampling population a global variable so that it isn't
# re-calculated
def get_rand_filename(allowTrailingDotSpace=False):
    validChars = string.letters + ' ' + '.'
    l = _FILENAME_LEN
    if allowTrailingDotSpace:
        fn = ''.join(random.sample(validChars * l, l))
    else:
        l = _FILENAME_LEN - 1
        fn = ''.join(random.sample(validChars * l, l)) \
             + ''.join(random.choice(list(set(validChars)
                                          - set(_WIN32_DISALLOWED_TRAILING_CHARACTERS))))
    return fn


_IN_MEMORY_MAX = 100 * 1024 * 1024
_BLOCK_STR_LEN = 1024  # A prime number
_RAND_STRING_LEN = 32
_FILLER_SUFFIX = 'tr' + 'ol' * (_BLOCK_STR_LEN - _RAND_STRING_LEN - 2)


def write_file(filepath, fsize, return_content=False):
    """
    Fill a file with fsize bytes of random contents.

    @param return_content whether to return the generated file content. Use with
    caution when fsize is large
    """

    content = ''

    with open(filepath, 'w') as f:
        # Determine the block size in bytes
        bsize = sys.getsizeof('a' * _RAND_STRING_LEN + _FILLER_SUFFIX)
        nblocks = fsize / bsize
        nblocksInMem = _IN_MEMORY_MAX / bsize

        population = string.letters * _RAND_STRING_LEN
        while nblocks > 0:
            randstr = ''.join(random.sample(population, _RAND_STRING_LEN)) \
                      + _FILLER_SUFFIX
            f.write(randstr)
            if return_content: content += randstr
            nblocks -= 1
            if nblocks % nblocksInMem == 0:
                f.flush()
                os.fsync(f.fileno())

        # write the remaining bytes
        remBytes = fsize - f.tell()
        while remBytes > 0:
            # Since a python string may not map 1:1 to a byte,
            # conservatively generate filler
            nchars = max(1, remBytes / 2)

            if nchars > _RAND_STRING_LEN:
                randstr = ''.join(random.sample(population, _RAND_STRING_LEN)) \
                          + _FILLER_SUFFIX[:(nchars - _RAND_STRING_LEN)]
            else:
                randstr = ''.join(random.sample(population, nchars))

            f.write(randstr)
            if return_content: content += randstr
            f.flush()
            os.fsync(f.fileno())

            remBytes = fsize - f.tell()

        if return_content: return content


def wait_path_to_disappear(path):
    """
    wait until the path is deleted
    """
    while os.path.exists(path): time.sleep(param.POLLING_INTERVAL)

# Wait For File/Dir Tree idea:
# 1 rdiffdir can be installed with homebrew on OSX, probably exists on cygwin
#   - http://linux.die.net/man/1/rdiffdir
#   - preliminary test did not work:
#   -> scp a directory to two linux boxes, compute sig_file, perform delta
#   -> somehow the delta was not empty. *perhaps I did not interpret the output
#      correctly*
# 2 md5deep can be installed with homebrew on OSX
#   - http://ubuntuforums.org/showthread.php?t=1516451
#   - md5deep -r -l . > /path/to/outputFile.txt
#   - sort -k 2 -o /path/to/sortedOutputFile.txt /path/to/outputFile.txt
#   - diff /path/to/sortedOutputFile1.txt /path/to/sortedOutputFile2.txt
#   - Downside: must scp the output file
# 3 python's filecmp and dircmp modules
# 4 Create a dirtree object, constructed with depth, ndirs, nfiles, SEED, etc.
#   - on creator side, call method .makeDir()
#   - on receiver, call method.wait_dir(), which locally constructs the dir
#      in some random local tmp directory, then uses md5 to compare.
#   - Downside: how to set timeout, and how to tell difference between
#      AeroFS taking a long time to sync, and waiting for dir creation.

#============================================================================
# Test Code
if __name__ == '__main__':
    init(0)
    r = os.path.join('.', get_rand_dirname('root_'))
    os.makedirs(r)
    make_dir_tree(r, 3, 2, 10, 1024)
