import os

from setuptools import setup, find_packages

requires = [
    'protobuf',
    'pycrypto'
]

# TODO (GS): Find a better name for this package
setup(name='aerofs-py-lib',
      version='0.2',
      description='AeroFS Common Python lib',
      packages=find_packages(),
      include_package_data=True,
      install_requires=requires,
      test_suite='unittests'
)
