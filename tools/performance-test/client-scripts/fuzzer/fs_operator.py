import glob
import os
import random
import re
import shutil
import time

import collections


HOME_DIR = os.environ['HOME']
ROOTANCHOR_PATTERN = re.compile(r'/rtroot/user\d+/device\d+/rootanchor/?(.*)')
USER_DEVICE_PATTERN = re.compile(r'/rtroot/user(\d+)/device(\d+).*')


class FSOperator(object):
    def __init__(self):
        self.file_lookahead = -1
        self.file_marker = 0
        self.file_object = 1

        self.roots = collections.defaultdict(dict)

    # Randomization

    def _random(self, user, device, dir_filter):
        path = self.get_root_folder(user, device)
        fs_object = self.get_object(path)

        while fs_object != self.file_marker:
            choices = dir_filter(fs_object)
            if not choices:
                return None

            choice = random.choice(choices)
            if choice == self.file_object:
                break

            path = os.path.join(path, choice)
            fs_object = fs_object[choice]

        return path

    def get_random_dirpath(self, user, device):
        dir_filter = lambda x: [self.file_object] + [
            k for k, v in x.iteritems() if v != self.file_marker]

        return self._random(user, device, dir_filter)

    def get_random_filepath(self, user, device):
        dir_filter = lambda x: [k for k, v in x.iteritems()
                                if v == self.file_marker]

        return self._random(user, device, dir_filter)

    def get_random_path(self, user, device):
        dir_filter = lambda x: x.keys() + [self.file_object]

        return self._random(user, device, dir_filter)

    # Helpers

    def get_next_filename(self):
        self.file_lookahead += 1
        return str(self.file_lookahead)

    @staticmethod
    def get_root_folder(user, device):
        return os.path.join(
            HOME_DIR, 'rtroot/user{}/device{}/rootanchor/folder{}'.format(
                user, device, device))

    @staticmethod
    def get_user_and_device(path):
        match = USER_DEVICE_PATTERN.match(path, len(HOME_DIR))
        if match:
            return match.group(1), match.group(2)

        raise Exception('could not find user and device from path {}'.format(
            path))

    @staticmethod
    def split_path(path):
        match = ROOTANCHOR_PATTERN.match(path, len(HOME_DIR))
        if match:
            return [x for x in match.group(1).split('/') if x]

        raise Exception("couldn't split path {} from aerofsroot".format(path))

    # Fuzz Operations

    def create_object(self, path, is_dir=random.choice((True, False))):
        parent, fs_object = os.path.split(path)
        if is_dir:
            self.get_object(parent)[fs_object] = {}
            os.mkdir(path)
            return

        self.get_object(parent)[fs_object] = self.file_marker
        self.update_object(path)

    def delete_object(self, path):
        parent_path, fs_object = os.path.split(path)
        parent = self.get_object(parent_path)

        if 'folder' in fs_object:
            # Do not delete root object
            return

        if parent[fs_object] == self.file_marker:
            os.remove(path)
        else:
            shutil.rmtree(path)

        del parent[fs_object]

    def get_object(self, path):
        user, device = self.get_user_and_device(path)
        current_file = self.roots[(user, device)]
        for path_component in self.split_path(path):
            current_file = current_file[path_component]

        return current_file

    def move_object(self, src, dest):
        old_path, old_name = os.path.split(src)
        old_parent = self.get_object(old_path)
        old_object = old_parent[old_name]

        new_path, new_name = os.path.split(dest)
        new_parent = self.get_object(new_path)

        if old_object != self.file_marker and src in dest:
            # Do not allow cyclical moves
            return

        if 'folder' in old_name:
            # Do not move root object
            return

        os.rename(src, dest)
        del old_parent[old_name]
        new_parent[new_name] = old_object

    @staticmethod
    def update_object(path):
        with open(path, 'w') as f:
            f.write(str(random.randint(0, 10000)))

    # Init

    def populate_rootanchor(self, user, device, objects, delay):
        for _ in xrange(objects):
            parent_path = self.get_random_dirpath(user, device)
            object_path = os.path.join(parent_path, self.get_next_filename())
            self.create_object(object_path)
            time.sleep(delay)


def get_device_count():
    return len(glob.glob(HOME_DIR + '/rtroot/user0/device*'))


def get_user_count():
    return len(glob.glob(HOME_DIR + '/rtroot/user*'))
