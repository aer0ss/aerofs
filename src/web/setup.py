import os

from setuptools import setup, find_packages

here = os.path.abspath(os.path.dirname(__file__))
README = open(os.path.join(here, 'README.txt')).read()
CHANGES = open(os.path.join(here, 'CHANGES.txt')).read()

# TODO: Add aerofs.py-lib here
requires = [
    'protobuf',
    'pyramid',
    'pyramid_debugtoolbar',
    'waitress',
    'requests',
    'pyramid_beaker',
    'Babel',
    'mock',
    ]

msg_extractors = {}
for package in find_packages():
    msg_extractors[package] = [
        ('**.py', 'python', None),
        ('**.mako', 'mako', None),
    ]

setup(name='web',
      version='0.0',
      description='web',
      long_description=README + '\n\n' +  CHANGES,
      classifiers=[
        "Programming Language :: Python",
        "Framework :: Pylons",
        "Topic :: Internet :: WWW/HTTP",
        "Topic :: Internet :: WWW/HTTP :: WSGI :: Application",
        ],
      author='',
      author_email='',
      url='',
      keywords='web pyramid pylons',
      message_extractors = msg_extractors,
      packages=find_packages(),
      include_package_data=True,
      zip_safe=False,
      install_requires=requires,
      tests_require=requires,
      test_suite="web",
      entry_points = """\
      [paste.app_factory]
      main = web:main
      """,
      )

