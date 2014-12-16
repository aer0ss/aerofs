from setuptools import setup

# If you want a package (something with an __init__.py)
# to wind up installed, you need to list it here.
packages = [
        "web",
        "web.views",
        "web.views.error",
        "web.views.maintenance",
        ]

setup(name='aerofs-bunker',
      version='0.0.1',
      description='AeroFS Maintenance Panel',
      classifiers=[
        "Programming Language :: Python",
        "Framework :: Pylons",
        "Topic :: Internet :: WWW/HTTP",
        "Topic :: Internet :: WWW/HTTP :: WSGI :: Application",
        ],
      author='AeroFS Team',
      author_email='team@aerofs.com',
      url='https://www.aerofs.com',
      keywords='bunker maintenance pyramid pylons',
      packages=packages,
      include_package_data=True,
      zip_safe=False,
      )
