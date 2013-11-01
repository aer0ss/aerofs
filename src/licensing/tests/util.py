#!/usr/bin/env python

import os.path
import unittest

keydir = os.path.join(os.path.dirname(__file__), 'test_keys')

class LicenseTestCase(unittest.TestCase):
    def gpg_dir(self):
        return keydir
