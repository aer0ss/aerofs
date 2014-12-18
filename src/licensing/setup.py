#!/usr/bin/env python

from setuptools import setup

setup(name='aerofs_licensing',
      version      = '0.0.1',
      description  = 'AeroFS license management utilities',
      author       = 'Drew Fisher',
      author_email = 'drew@aerofs.com',
      requires     = ["gpgme"],
      provides     = ["aerofs_licensing"],
      url          = 'https://www.aerofs.com',
      packages     = ['aerofs_licensing','aerofs_licensing.crypto'],
      scripts      = ['scripts/license-tool'],
      package_data = {'aerofs_licensing': ['crypto/license_root_public/*']},
      )
