#!/usr/bin/env python

from distutils.core import setup

setup(name='lizard',
      version      = '0.0.1',
      description  = 'AeroFS license management website',
      author       = 'Drew Fisher',
      author_email = 'drew@aerofs.com',
      provides     = ["lizard"],
      url          = 'https://www.aerofs.com',
      packages     = ['lizard'],
      #package_data = {'lizard': ['templates/*']},
      )

