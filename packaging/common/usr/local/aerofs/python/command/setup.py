import os
from setuptools import setup, find_packages

here = os.path.abspath(os.path.dirname(__file__))

requires = [
        ]

setup(name='command',
      version='0.14',
      description='command',
      long_description='command',
      packages=find_packages(),
      include_package_data=True,
      zip_safe=False,
      install_requires=requires,
      tests_requires=requires,
      test_suite="command",
      entry_points = """\
      [paste.app_factory]
      main = command:main
      """,
      )
