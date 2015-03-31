"""
When an existing object is moved over another existing object of the same type,
the scanner/linker should update the target object instead of renaming it to
make way and copying source.
"""
import shutil
import common

def replace(path, content, r):
    # create a temporary file *inside the rtroot*
    tmp = path + ".tmp"
    with open(tmp, 'w') as f: f.write(content)

    # make sure AeroFS create a new object for that temp file
    # NB: ideally we'd like such temporary objects to not be created
    # which should happen if they disappear fast enough or are explicitly
    # added to the ignore list but we need to be sure we react correctly
    # even in cases were such temporary objects ARE created
    # COROLLARY: we need to clean up tombstones at some point...
    r.wait_path(tmp)

    # move over
    shutil.move(tmp, path)

spec = common.replace_test(replace)
