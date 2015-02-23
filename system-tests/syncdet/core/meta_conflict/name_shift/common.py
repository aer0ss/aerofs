import os
import collections
from os.path import dirname
from syncdet.case import sync, actor_id, actor_count
from lib import files
from lib import ritual
from lib.files import dirtree
from lib.network_partition import NetworkPartition

class NameShiftTester:
    """
    This 'abstract' class defines three public methods to test name shift
    scenarios.
    One of the three public methods are to be run by each syncdet actor
     * creator: creates the original folders and defines the base set of names
     * shifter: syncs the folders and renames to a shifted set of names
     * receiver: is a "non-participant" which waits until the creator and
                 shifter are done, before syncing the folders
    """
    def __init__(self, file_names, file_contents, orig_dir_names, conflict_dir_names):

        assert len(file_names) == len(file_contents) == len(conflict_dir_names) == len(orig_dir_names)

        self._file_names = file_names
        self._file_contents = file_contents
        self._orig_dir_names = orig_dir_names
        self._conflict_dir_names = conflict_dir_names


    def creator(self):
        # Create the folders then wait til the shifter peer syncs them
        dt = self._create_original_dirtree()
        dt.write(verbose=True)

        sync.sync(0)

        with NetworkPartition():
            self._create_conflict_and_subfiles(self._conflict_dir_names)

        self._wait_for_final_state()

        # wait until the two actors involved with aliasing are resolved
        sync.sync(3)

    def shifter(self):
        # Wait for the pair of folders to sync here
        dt = self._create_original_dirtree()
        dirtree.wait_for_any(dt)

        sync.sync(0)

        # This peer will rename the folders to a set of names that are rotated
        # relative to the names used by the creator
        conf_names = collections.deque(self._conflict_dir_names)
        conf_names.rotate()
        with NetworkPartition():
            self._create_conflict_and_subfiles(conf_names)

        self._wait_for_final_state()

        # wait until the two actors involved with aliasing are resolved
        sync.sync(3)

    def receiver(self):
        # All other actors should pause syncing until the other two sort out the
        # aliasing
        with NetworkPartition():
            for i in range(4): sync.sync(i)

        # Now that the aliasing is resolved, download the files to the receiver
        self._wait_for_final_state()

    def _wait_for_final_state(self):
        """
        For both the creator and shifter actors, one unique file has been
        created in each directory. If the test involves N directories, we
        therefore expect N files in each directory. Because conflict2 on
        actor 1 has the same OID as conflict1 on actor 0 (etc.),
        one of the directories will be renamed (depending on
        OID and DID ordering).

        The AeroFS conflict renaming algorithm can result in
        naming one of the directories like conflict1 (2) for example.  If
        there are n directories, there are 2+n*2 total acceptable states.
        """

        # A list of dictionaries representing the files expected to be in
        # each directory.  e.g. dict_files[0] is a dictionary of
        # files/content expected in the first directory
        dict_files = [ dict(zip([self._construct_file_name(name, s) for s in (0, 1)], [content] * 2))
                       for name, content in zip(self._file_names, self._file_contents) ]

        dts = self._create_list_of_acceptable_final_dirtrees(dict_files)

        # Wait for the filesystem to match only one of these DirTrees
        dirtree.wait_for_any(*dts)

    def _create_list_of_acceptable_final_dirtrees(self, dict_files):
        """
        Create the many permutations of conflict directory names with (2)
        suffixes, then each of those permutations with dict_files to return
        all acceptable DirTrees.
        """

        # Create a list of the two directory name permutations:
        # 1) the original list
        # 2) the list rotated by the deque.rotate() method
        d = collections.deque(self._conflict_dir_names)
        d.rotate()
        conflict_name_permutations = [list(self._conflict_dir_names), list(d)]

        # Because the AeroFS conflict renaming algorithm might add the suffix
        # ' (2)' to one of the directories, we create a list here of all
        # permutations of suffixes. For 3 directories it would look like
        # [['', '', ''], [' (2)', '', ''], ['', ' (2)', ''], ['', '', ' (2)']]
        # actually multiple directories might get renamed. the following are also possible (though less likely)
        # [' (2)', ' (2)', ''], [' (2)', '', ' (2)'], ['', ' (2)', ' (2)']
        count_names = len(self._conflict_dir_names)
        suffixes = [['']*count_names]
        suffixes.extend(gen_suffixes(count_names, ' (2)'))
        for i in range(count_names):
            suffixes.extend([['']*i + [' (2)'] + s for s in gen_suffixes(count_names-1-i, ' (2)')])

        # Concatenate all permutations of suffix vs list names.
        directory_name_lists = [map(''.join, zip(n, s))
                                for n in conflict_name_permutations
                                for s in suffixes]

        # Return a list of DirTrees, based on all the permutations created
        # above
        return [dirtree.InstanceUniqueDirTree(dict(zip(names, dict_files)))
                for names in directory_name_lists]



    def _create_conflict_and_subfiles(self, new_dir_names):
        """
        @param new_dir_names list of new names for the directories in
               self._orig_dir_names
        """
        sync.sync(1)

        # Rename the synced folders to the conflict names
        for (src, dst) in zip(self._orig_dir_names, new_dir_names):
            os.rename(os.path.join(files.instance_unique_path(), src),
                os.path.join(files.instance_unique_path(), dst))

        # Write to one globally unique file in each directory
        # 1) construct a DirTree from the following dictionary description
        # 2) create the DirTree on the file system
        dict_dirtree = {}
        for (dir_name, file_name, content) in zip(new_dir_names,
            self._file_names, self._file_contents):
            dict_dirtree[dir_name] = {
                self._construct_file_name(file_name, actor_id()) : content }

        dt = dirtree.InstanceUniqueDirTree(dict_dirtree)
        dt.write(ignore_existing_dir=True, verbose=True)

        r = ritual.connect()

        # Wait for ritual to pick up each leaf node in the DirTree
        for rel_path in dt.leaf_nodes():
            r.wait_path(os.path.join(
                dirname(files.instance_unique_path()), rel_path))

        sync.sync(2)

    def _create_original_dirtree(self):
        dir_dictionary = dict(
            zip(self._orig_dir_names, [{}]*len(self._orig_dir_names)) )
        return dirtree.InstanceUniqueDirTree(dir_dictionary)

    def _construct_file_name(self, file_name, suffix):
        return "{0}.{1}".format(file_name, suffix)


def gen_suffixes(n, suffix):
    return [['']*i + [suffix] + ['']*(n-1-i) for i in range(n)]