import os
import random
import shutil


class Client(object):
    def __init__(self, fs, user, device):
        self.fs = fs

        self.user = user
        self.device = device

    def initialize(self, objects, delay):
        try:
            shutil.rmtree(self.fs.get_root_folder(self.user, self.device))
        except Exception:
            pass

        self.fs.create_object(self.fs.get_root_folder(self.user, self.device),
                              is_dir=True)
        self.fs.populate_rootanchor(self.user, self.device, objects, delay)

    def operate(self):
        op = random.choice(('create', 'delete', 'move', 'rename', 'update'))
        getattr(self, op)()

    # Fuzz Operations

    def create(self):
        path = os.path.join(self.fs.get_random_dirpath(self.user, self.device),
                            self.fs.get_next_filename())
        self.fs.create_object(path)

    def delete(self):
        path = self.fs.get_random_path(self.user, self.device)
        self.fs.delete_object(path)

    def move(self):
        src = self.fs.get_random_path(self.user, self.device)
        dest = self.fs.get_random_dirpath(self.user, self.device)

        if random.choice((True, False)):
            # Also change file name
            dest = os.path.join(dest, self.fs.get_next_filename())
        else:
            dest = os.path.join(dest, os.path.basename(src))

        self.fs.move_object(src, dest)

    def rename(self):
        src = self.fs.get_random_path(self.user, self.device)
        dest = os.path.join(os.path.dirname(src), self.fs.get_next_filename())
        self.fs.move_object(src, dest)

    def update(self):
        path = self.fs.get_random_filepath(self.user, self.device)
        if path:
            self.fs.update_object(path)
