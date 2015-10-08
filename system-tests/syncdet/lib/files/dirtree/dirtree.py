"""
A DirTree abstractly represents a directory tree of files and subfolders. It
is constructed using a dictionary to represent the tree,
where keys are file names, and values are either i) a string representing
file content or ii) a dictionary representing a subdirectory tree.
e.g.
        dt = DirTree('dirtree_name',
                {
                'f3': 'content3',
                'd1': {'f12': 'content12',
                       'f11': 'content11',
                       'd12': {}},
                'f2': 'content2',
                'd2': {'d21': {'d211': {}}},
                'f1': 'content1',
            }
        )

After construction, users of this class can write a physical instance of the
DirTree rooted in some parent directory:
e.g.
        dt.write('~/parent_folder/')

Users can also verify that the DirTree represents some directory in the
physical file system
e.g. the following should return True

        dt.represents('~/parent_folder/dirtree_name')

With less strictness, users can verify that the contents of a DirTree exist
in the file system. This test is more like a subset test; it should return True

        dt.exists_in('~/parent_folder')
"""
import os
from os.path import join
from hashlib import md5
import types
import collections
import itertools

_MAX_FILE_SIZE = 4096

def _string_for_hash(string):
    """@return the string, ready for a hashlib object call update using it"""
    if isinstance(string, str):
        return string
    else:
        assert isinstance(string, unicode)
        return string.encode('utf-8')

class _FSObject:
    def __init__(self, rel_path, hash):
        """
        @param rel_path relative path of this file system object to the root
        directory in which it is expected to reside
        """
        self._rel_path = rel_path
        self._hash = hash.copy()

    def _digest(self):
        """
        @return the digest of the hash object computed for this file system
        object
        """
        return self._hash.hexdigest()

    def __eq__(self, other):
        return self._digest() == other._digest()

class _FileSystemRaceError(Exception): pass

class _PhysicalDirTree(_FSObject):
    """
    Construct an _FSObject with a hash digest, from a physical directory
    """
    def __init__(self, path, ignore_content=None, ignore_file=None):
        """
        create a DirTree with hash from the given path
        """
        path = os.path.normpath(path)
        if not os.path.isdir(path):
            raise _FileSystemRaceError

        # Extract the parent (root) directory and the name of the given
        # physical directory to inspect.
        root_path, name = os.path.split(path)

        hash = md5()

        stack = [name]
        while stack:
            # File system object path relative to the root_path above
            rel_path = stack.pop()
            hash.update(_string_for_hash(rel_path))

            # Absolute path of the object
            abs_path = join(root_path, rel_path)

            if os.path.isfile(abs_path):
                if ignore_content is not None and os.path.basename(rel_path) in ignore_content:
                    print 'ignored content for {} [act]'.format(rel_path)
                else:
                    try:
                        with open(abs_path, 'r') as f:
                            # This implementation only expects to deal with small files
                            # in the file system. See Comment A
                            assert os.path.getsize(abs_path) < _MAX_FILE_SIZE
                            hash.update(f.read())
                    except EnvironmentError:
                        raise _FileSystemRaceError

            elif os.path.isdir(abs_path):
                try:
                    for child in sorted(os.listdir(abs_path), reverse=True):
                        if not (ignore_file and child in ignore_file):
                            stack.append(join(rel_path, child))
                except EnvironmentError:
                    raise _FileSystemRaceError
            else:
                # abs_path was previously listed in os.listdir,
                # but due to a race, no longer exists on the filesystem
                raise _FileSystemRaceError

        _FSObject.__init__(self, rel_path, hash)


class _File(_FSObject):
    """
    Helper class that implements similar __init__() and create() signatures
    for files, instead of directories
    """
    def __init__(self, rel_path, content, ignore_content=None, hash=None):
        """
        @param rel_path see _FSObject.__init__
        @param content string for the file content (shouldn't be huge,
                assumed to fit in memory)
        @param hash a hash object (from hashlib) to be updated with this file's
                name and content.
        """

        # Comment A
        # For this "manual" implementation of DirTree, where developers write
        # the content, we expect files to be short (arbitrarily less than
        # 4096 characters). Another implementation will need to read, write,
        # and hash file chunks more efficiently
        assert len(content) < _MAX_FILE_SIZE
        self._content = content

        rel_path = os.path.normpath(rel_path)

        if not hash: hash = md5()
        hash.update(_string_for_hash(rel_path))
        if ignore_content is not None and os.path.basename(rel_path) in ignore_content:
            print 'ignored content for {} [exp]'.format(rel_path)
        else:
            hash.update(self._content.encode('utf-8'))

        _FSObject.__init__(self, rel_path, hash)

    def write(self, root_path, ignore_existing_dir, verbose):
        parent_path = join(root_path, os.path.dirname(self._rel_path))
        assert os.path.isdir(parent_path)

        if verbose:
            print 'DT: write {0} with "{1}"'.format(self._rel_path,
                self._content)

        with open(join(root_path, self._rel_path), 'w') as f:
            f.write(self._content.encode('utf-8'))

    def leaf_nodes(self):
        return [self._rel_path]


class DirTree(_FSObject):
    def __init__(self, rel_path, children, ignore_content=None, ignore_file=None, hash=None):
        """
        @param rel_path see _FSObject.__init__
        @param children a mapping of name:content pairs
                n.b. for a file, content is a string, for a directory,
                content is another dictionary of similar structure
        @param hash a hash object (from hashlib) to be updated with this
                DirTree's name and children name and content.
        """
        self._children = []
        self._ignore_content = ignore_content
        self._ignore_file = ignore_file

        assert isinstance(children, collections.Mapping)

        if not hash: hash = md5()

        rel_path = os.path.normpath(rel_path)
        hash.update(_string_for_hash(rel_path))

        for n in sorted(children.keys()):
            content = children[n]
            child_rel_path = join(rel_path, n)
            f = None
            if isinstance(content, types.StringTypes):
                f = _File(child_rel_path, content, ignore_content=ignore_content, hash=hash)
            else:
                f = DirTree(child_rel_path, content, ignore_content=ignore_content, hash=hash)

            self._children.append(f)

        _FSObject.__init__(self, rel_path, hash)

    def write(self, root_path, ignore_existing_dir=False, verbose=False):
        """
        Write this DirTree to the physical file system,
        rooted inside root_path
        @param ignore_existing_dir: ignore errors due to existing directories
        """
        path_to_create = join(root_path, self._rel_path)
        if ignore_existing_dir and os.path.isdir(path_to_create):
            # no-op since the directory already exists
            pass
        else:
            # Do *not* use os.makedirs as it is assumed the parent directory
            # should have been created before this directory
            if verbose: print 'DT: mkdir {0}'.format(self._rel_path)
            os.mkdir(path_to_create)

        for f in self._children:
            f.write(root_path, ignore_existing_dir, verbose)

    def represents(self, root_path, verbose=False):
        try:
            phys_dirtree = _PhysicalDirTree(root_path, ignore_content=self._ignore_content, ignore_file=self._ignore_file)
        except _FileSystemRaceError:
            # The file system changed underneath while scanning the
            # physical directory.
            return False

        if verbose:
            print 'DT: {0} vs {1}'.format(phys_dirtree._digest(),
                self._digest())

        return self == phys_dirtree

    def exists_in(self, parent_path):
        """
        @param parent_path: path to the directory which will contain this dt
        @return True iff this DirTree represents a child of parent_path
        """
        leaf_rel_paths = self.leaf_nodes()
        leaf_abs_paths = [join(parent_path, rel) for rel in leaf_rel_paths]
        return all([os.path.exists(abs) for abs in leaf_abs_paths])


    def leaf_nodes(self):
        """
        @return a list of paths, relative to the root of the DirTree,
        for all leaf nodes (files or empty directories) in the DirTree
        """
        if not self._children: return [self._rel_path]

        return list(itertools.chain(*[fs_obj.leaf_nodes() for fs_obj in
                                     self._children]))
