from distutils.core import setup

# If you want a package (something with an __init__.py)
# to wind up installed, you need to list it here.
packages = [
    "web",
    "web.views",
    "web.views.accept",
    "web.views.devices",
    "web.views.download",
    "web.views.error",
    "web.views.login",
    "web.views.marketing",
    "web.views.password_reset",
    "web.views.payment",
    "web.views.shared_folders",
    "web.views.signup",
    "web.views.org_users",
    "web.views.org_settings",
    "web.views.unsubscribe",
    "web.views.unsubscribe.templates",
]

setup(name='aerofs-web',
      version='0.0.1',
      description='web',
      classifiers=[
          "Programming Language :: Python",
          "Framework :: Pylons",
          "Topic :: Internet :: WWW/HTTP",
          "Topic :: Internet :: WWW/HTTP :: WSGI :: Application",
      ],
      author='AeroFS Team',
      author_email='team@aerofs.com',
      url='https://www.aerofs.com',
      keywords='web pyramid pylons',
      packages=packages,
      include_package_data=True,
      zip_safe=False,
      entry_points = """
      [paste.app_factory]
      main = web:main
      """,
      )
