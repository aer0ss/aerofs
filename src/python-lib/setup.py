import os

from setuptools import setup, find_packages

# TODO (GS): Find a better name for this package
setup(name='aerofs-py-lib',
      version='0.1',
      description='AeroFS Python lib',
      packages=find_packages(),
      include_package_data=True,
)

