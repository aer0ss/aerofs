from lib.app.install import get_installer


def reinstall():
    installer = get_installer()
    installer.install(clean_install=False)


spec = {
    'default': reinstall,
    'timeout': 120
}

