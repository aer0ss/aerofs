from distutils.core import setup

requires = [
    'protobuf',
]

packages = ["aerofs_common", "aerofs_common._gen",
            "aerofs_ritual", "aerofs_ritual.gen",
            "aerofs_sp",     "aerofs_sp.gen",
            ]

# TODO (GS): Find a better name for this package
setup(name= 'aerofs-py-lib',
      version              = '0.2',
      description          = 'AeroFS Common Python lib',
      author               = "aerofs",
      author_email         = "team@aerofs.com",
      url                  = "https://www.aerofs.com",
      provides             = ["aerofs_common", "aerofs_ritual", "aerofs_sp"],
      packages             = packages,
      include_package_data = True,
      install_requires     = requires,
)
