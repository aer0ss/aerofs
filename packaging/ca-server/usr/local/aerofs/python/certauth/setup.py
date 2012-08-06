import os
from setuptools import setup, find_packages

here = os.path.abspath(os.path.dirname(__file__))

setup(name='certauth',
      version='0.0',
      description='certauth',
      long_description='certauth',
      classifiers=[
        "Programming Language :: Python",
        "Topic :: Internet :: WWW/HTTP",
        "Topic :: Internet :: WWW/HTTP :: WSGI :: Application",
        ],
      author='',
      author_email='',
      url='',
      keywords='certificate authority',
      packages=find_packages(),
      include_package_data=True,
      zip_safe=False,
      test_suite="certauth",
      entry_points = """\
      [paste.app_factory]
      main = certauth:main
      """,
      )
