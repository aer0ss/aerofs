"""
Test the DirTree class and its helper classes with
$ python -m unittest -v test_dirtree
"""

import unittest
import tempfile
import os
from .. import aero_shutil as shutil
from os.path import join

from dirtree import DirTree

class TestDirTree(unittest.TestCase):

    def setUp(self):
        # A sample DirTree object used in all the tests
        self._dtname = 'dirtree'
        self._dt = DirTree(self._dtname,
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

        # Create a temporary directory in which to create physical
        # representations of DirTrees
        self._parent_path = tempfile.mkdtemp()
        self.assertTrue(os.path.isdir(self._parent_path))

    def tearDown(self):
        self.assertTrue(os.path.isdir(self._parent_path))
        shutil.rmtree(self._parent_path)

    def test_directories_match_when_testing_same_object_as_created(self):
        self._dt.write(self._parent_path)

        self.assertTrue(
            self._dt.represents(join(self._parent_path, self._dtname))
        )

    def test_directories_dont_match_when_not_created(self):
        # Make an empty directory of name self._dtname
        os.mkdir(join(self._parent_path, self._dtname))

        self.assertFalse(
            self._dt.represents(join(self._parent_path, self._dtname))
        )

    def test_directory_exists_when_testing_same_object_as_created(self):
        self._dt.write(self._parent_path)
        self.assertTrue(self._dt.exists_in(self._parent_path))

    def test_directory_does_not_exist_when_not_created(self):
        # Make an empty directory of name self._dtname
        os.mkdir(join(self._parent_path, self._dtname))
        self.assertFalse(self._dt.exists_in(self._parent_path))

    def test_directories_match_when_same_tree_but_different_construction_order(self):
        dt_diff_order = DirTree(self._dtname,
                {
                'f3': 'content3',
                'f1': 'content1',
                'd2': {'d21': {'d211': {}}},
                'd1': {'f12': 'content12',
                       'd12': {},
                       'f11': 'content11'
                 },
                'f2': 'content2',
            }
        )

        self.assertEqual(self._dt, dt_diff_order)

        # Write the directory tree using the DirTree of different order,
        # but assert that the self._dt represents the directory
        dt_diff_order.write(self._parent_path)

        self.assertTrue(
            self._dt.represents(join(self._parent_path, self._dtname))
        )

    def test_directories_dont_match_when_content_differs(self):
        dt_diff_content = DirTree(self._dtname,
                {'d1': {'f11': 'lala11',
                        'f12': 'lala12',
                        'd12': {}},
                 'd2': {'d21': {'d211': {}}},
                 'f1': 'lala1',
                 'f2': 'lala2',
                 'f3': 'lala3'
            }
        )

        self.assertNotEqual(self._dt, dt_diff_content)

        # Physically write the DirTree of same structure but different content
        dt_diff_content.write(self._parent_path)

        self.assertFalse(
            self._dt.represents(join(self._parent_path, self._dtname))
        )

    def test_leaves_returns_relative_paths_of_empty_dirs_and_files(self):
        expected_leaves = set(
            [join(self._dtname, rel_path) for rel_path in
             ['f3',
              join('d1', 'f12'),
              join('d1', 'f11'),
              join('d1', 'd12'),
              'f2',
              join('d2', 'd21', 'd211'),
              'f1']
            ])

        actual_leaves = set(self._dt.leaf_nodes())
        self.assertEqual(expected_leaves, actual_leaves)
