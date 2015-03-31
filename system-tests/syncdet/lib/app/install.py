import os
import shutil
import stat
import subprocess
import sys
import tarfile
import time
import zipfile

import requests
from syncdet import case

import aerofs_proc
from aerofs_common.param import POLLING_INTERVAL
from cfg import get_cfg, get_native_homedir, is_teamserver


#####           #####
### LIB FUNCTIONS ###
#####           #####

def del_rw(action, name, exc):
    if action == os.remove or action == os.rmdir:
        os.chmod(name, stat.S_IWRITE | stat.S_IREAD)
        action(name)
    else:
        raise


def rm_rf(path):
    if not os.path.exists(path):
        return
    if os.path.isdir(path):
        shutil.rmtree(path, onerror=del_rw)
    elif os.path.isfile(path):
        os.remove(path)
    else:
        raise ValueError('{} is not a file or a directory - cowardly refusing to delete it'.format(path))


def mkdir_p(path):
    if not os.path.isdir(path):
        os.makedirs(path)


def get_client_unattended_setup_dict():
    return {
        'userid': case.local_actor().aero_userid,
        'password': case.local_actor().aero_password,
    }


def get_teamserver_unattended_setup_dict():
    details = getattr(case.local_actor(), 'details', {})
    d = {
        'userid': case.local_actor().aero_userid,
        'password': case.local_actor().aero_password,
        'storage_type': details['storage_type']
    }
    if details['storage_type'] == 'S3':
        d.update({
            's3_access_key': details['s3_access_key'],
            's3_bucket_id': details['s3_bucket_id'],
            's3_encryption_password': details['s3_encryption_password'],
            's3_secret_key': details['s3_secret_key']
        })
    return d


def get_transport_flags(transport):
    flags = {
        'tcp': ['nozephyr', 'nostun'],
        'zephyr': ['notcp', 'nostun'],
        'jingle': ['notcp', 'nozephyr'],
        'default': [],
        None: []
    }
    try:
        return flags[transport]
    except KeyError:
        raise ValueError('unknown transport: {}'.format(transport))


def download_file(url, filename):
    print 'GET {}'.format(url)
    r = requests.get(url, stream=True)
    content_length = int(r.headers.get('content-length'))
    print 'preparing to download {} bytes'.format(content_length)
    downloaded, n_printed, width = 0, None, 60
    with open(filename, 'wb') as f:
        for chunk in r.iter_content(chunk_size=8192):
            if chunk is not None:
                f.write(chunk)
                f.flush()
                downloaded += len(chunk)
                n_to_print = (width-3) * downloaded / content_length
                if n_to_print > n_printed:
                    print '[{}>'.format(n_to_print*'=').ljust(width-1) + ']'
                    n_printed = n_to_print


def extract(source, base, destdir):
    # custom unzip required to
    #       1. preserve file permissions
    #       2. skip the wrapping 'Release' dir
    with zipfile.ZipFile(source) as z:
        for name in z.namelist():
            zi = z.getinfo(name)
            unix_perm = (zi.external_attr >> 16) & 0x0fff
            targetpath = zi.filename.replace(base, destdir)

            # create missing parent directories if needed
            mkdir_p(os.path.dirname(targetpath))

            # TODO: handle symlinks

            # create dir
            if zi.filename[-1] == '/':
                if not os.path.isdir(targetpath):
                    os.mkdir(targetpath)
                continue

            # copy file contents from zip
            source = z.open(zi)
            target = file(targetpath, "wb")
            shutil.copyfileobj(source, target)
            source.close()
            target.close()
            # preserve permissions
            os.chmod(targetpath, unix_perm)


def ensure_site_config_present(approot):
    print 'ensure site-config.properties is present in {}'.format(approot)
    site_config_file = os.path.join(approot, 'site-config.properties')
    if not os.path.isfile(site_config_file):
        host = case.local_actor().aero_host
        client_config_url = 'https://{}/config/client'.format(host)
        r = requests.get(client_config_url, verify=False)
        with open(site_config_file, 'w') as f:
            f.write(r.text)


def extra_rtroot_flags():
    try:
        return case.local_actor().aero_flags or []
    except AttributeError:
        return []


def remove_auxroot(root_anchor):
    print 'removing auxroot...'
    p = os.path.dirname(root_anchor)
    # for absolute cleanliness remove *ALL* auxroots left over by previous installs
    for d in os.listdir(p):
        if d.startswith('.aerofs.aux.'):
            print 'remove auxroot: {}'.format(d)
            rm_rf(os.path.join(p, d))

#####                      #####
### Installer Parent Classes ###
#####                      #####

class BaseAeroFSInstaller(object):

    ### Installer-specific data ###

    _installers_path_base = 'static/installers'

    def __init__(self):
        self._cfg = get_cfg()
        self._rtroot_flags = ['lol', 'ac'] + extra_rtroot_flags()

    def get_ui_name(self):
        return self._cfg.get_ui_name()

    def get_approot(self):
        return self._cfg.get_approot()

    def get_rtroot(self):
        return self._cfg.get_rtroot()

    def get_default_root_anchor(self):
        raise NotImplementedError

    def get_installer_name(self):
        raise NotImplementedError

    def get_installer_path(self):
        return os.path.join(case.deployment_folder_path(), self.get_installer_name())

    def get_setup_file_path(self):
        return os.path.join(self.get_rtroot(), 'unattended-setup.properties')


    ### Main public method ###

    def install(self, transport=None, clean_install=True):
        aerofs_proc.stop_all()
        self.remove_approot()
        if clean_install:
            self.remove_root_anchor()
            self.remove_rtroot()
            self.create_rtroot_flags(transport)
            self.configure_unattended_setup()
        if not os.path.exists(self.get_installer_path()):
            self.download_installer()
        self.run_installer()
        self.wait_for_pb_file()
        aerofs_proc.wait_for_daemon()
        print '{} up and running'.format(get_cfg().did().get_hex())


    ### Utility Methods ###

    def download_installer(self):
        print 'downloading installer...'
        host = case.local_actor().aero_host
        installer_uri = "https://{}/{}/{}".format(host, self._installers_path_base, self.get_installer_name())
        download_file(installer_uri, self.get_installer_path())

    def remove_approot(self):
        print 'removing approot...'
        rm_rf(self.get_approot())

    def remove_root_anchor(self):
        print 'removing root anchor...'
        root_anchor = self.get_default_root_anchor()
        rm_rf(root_anchor)
        remove_auxroot(root_anchor)

    def remove_rtroot(self):
        print 'removing rtroot...'
        rm_rf(self.get_rtroot())

    def create_rtroot_flags(self, transport=None):
        print 'creating rtroot flags...'
        self._rtroot_flags += get_transport_flags(transport)
        mkdir_p(self.get_rtroot())
        for flag in self._rtroot_flags:
            open(os.path.join(self.get_rtroot(), flag), 'wb').close()
        # while we're provisioning the rtroot, create the syncdet user data folder
        mkdir_p(case.user_data_folder_path())

    def get_unattended_setup_dict(self):
        raise NotImplementedError

    def configure_unattended_setup(self):
        print 'configuring unattended setup...'
        mkdir_p(self.get_rtroot())
        unattended_setup_values = self.get_unattended_setup_dict().iteritems()
        unattended_setup_str = '\n'.join('{}={}'.format(k, v) for k, v in unattended_setup_values)
        with open(self.get_setup_file_path(), 'w') as setupfile:
            setupfile.write(unattended_setup_str)

    def run_installer(self):
        raise NotImplementedError

    def wait_for_pb_file(self):
        print 'waiting for pb file...'
        pb_path = os.path.join(get_cfg().get_rtroot(), 'pb')
        while not os.path.isfile(pb_path):
            time.sleep(POLLING_INTERVAL)


#####                     #####
### Linux Installer Classes ###
#####                     #####

class BaseLinuxInstaller(BaseAeroFSInstaller):

    def get_untar_dir(self):
        return 'aerofs'

    def get_cli_path(self):
        raise NotImplementedError

    def run_installer(self):
        print 'untaring tgz installer...'
        rm_rf(self.get_untar_dir())
        with tarfile.open(self.get_installer_path()) as tar:
            tar.extractall(path=os.path.dirname(self.get_untar_dir()))

        ensure_site_config_present(os.path.join(self.get_untar_dir(), 'shared'))

        print 'starting cli...'
        untarred_cli_path = os.path.join(self.get_untar_dir(), self.get_ui_name())
        case.background.start_process(untarred_cli_path)


class ClientLinuxInstaller(BaseLinuxInstaller):

    def get_installer_name(self):
        return 'aerofs-installer.tgz'

    def get_default_root_anchor(self):
        return os.path.join(os.path.expanduser('~'), 'AeroFS')

    def get_unattended_setup_dict(self):
        return get_client_unattended_setup_dict()


class TeamServerLinuxInstaller(BaseLinuxInstaller):

    def get_installer_name(self):
        return 'aerofsts-installer.tgz'

    def get_default_root_anchor(self):
        return os.path.join(os.path.expanduser('~'), 'AeroFS Team Server Storage')

    def get_unattended_setup_dict(self):
        return get_teamserver_unattended_setup_dict()


#####                      #####
### Win Installer Classes ###
#####                      #####

class BaseWinInstaller(BaseAeroFSInstaller):

    def prime_dns_cache(self):
        # SIGH...this is unfortunately necessary
        #
        # If the DNS cache is not primed for a hostname, the guest will make a DNS request to the
        # host, receive an ip address, prime the cache, and then promptly fail because it "Couldn't
        # resolve host". This seems to be a bug in Cygwin. If you try again immediately, it will
        # read the ip from the DNS cache and successfully make the HTTP request.
        #
        # Making the HTTP request to the ip instead of the hostname fails when the cert is checked,
        # since the cert is signed for the hostname. So, the easiest way to work around this is to
        # catch at most one connection error, ensuring that the DNS cache is primed on the second
        # request.
        try:
            print 'priming dns cache'
            host = case.local_actor().aero_host
            config_url = "https://{}/{}/{}".format(host, 'config', 'client')
            requests.get(config_url, verify=False)
        except:
            pass

    def download_installer(self):
        self.prime_dns_cache()
        super(BaseWinInstaller, self).download_installer()

    def run_installer(self):
        # Make executable on Win
        old_mode = os.stat(self.get_installer_path()).st_mode
        new_mode = old_mode | stat.S_IXOTH | stat.S_IXGRP | stat.S_IXUSR
        os.chmod(self.get_installer_path(), new_mode)

        if 'LOCALAPPDATA' not in os.environ:
            os.environ['LOCALAPPDATA'] = self._cfg.get_local_appdata_path()
        cmd = os.path.join('.', self.get_installer_path())

        mkdir_p(self.get_approot())
        self.prime_dns_cache()
        ensure_site_config_present(self.get_approot())

        print 'running', cmd
        subprocess.Popen(cmd)


class ClientWinInstaller(BaseWinInstaller):

    def get_installer_name(self):
        return 'AeroFSInstall.exe'

    def get_default_root_anchor(self):
        return os.path.join(get_native_homedir(), 'My Documents', 'AeroFS')

    def get_unattended_setup_dict(self):
        return get_client_unattended_setup_dict()


class TeamServerWinInstaller(BaseWinInstaller):

    def get_installer_name(self):
        return 'AeroFSTeamServerInstall.exe'

    def get_default_root_anchor(self):
        return os.path.join(get_native_homedir(), 'My Documents', 'AeroFS Team Server Storage')

    def get_unattended_setup_dict(self):
        return get_teamserver_unattended_setup_dict()


#####                   #####
### OSX Installer Classes ###
#####                   #####

class BaseOSXInstaller(BaseAeroFSInstaller):

    def get_app_name(self):
        raise NotImplementedError

    def get_app_install_dir(self):
        return os.path.join(os.path.expanduser('~'), 'Applications')

    def run_installer(self):
        print 'unzipping app...'
        rm_rf(self.get_app_install_dir())
        extract(self.get_installer_path(), 'Release', self.get_app_install_dir())

        ensure_site_config_present(self.get_approot())

        print 'starting cli...'
        case.background.start_process(get_cfg().get_ui_cmd(), key=get_cfg().get_ui_name())


class ClientOSXInstaller(BaseOSXInstaller):

    def get_installer_name(self):
        return 'aerofs-osx.zip'

    def get_app_name(self):
        return 'AeroFS.app'

    def get_default_root_anchor(self):
        return os.path.join(os.path.expanduser('~'), 'AeroFS')

    def get_unattended_setup_dict(self):
        return get_client_unattended_setup_dict()


class TeamServerOSXInstaller(BaseOSXInstaller):

    def get_installer_name(self):
        return 'aerofsts-osx.zip'

    def get_app_name(self):
        return 'AeroFSTeamServer.app'

    def get_default_root_anchor(self):
        return os.path.join(os.path.expanduser('~'), 'AeroFS Team Server')

    def get_unattended_setup_dict(self):
        return get_teamserver_unattended_setup_dict()


#####                  #####
### Get Installer Method ###
#####                  #####

def get_installer():
    if 'linux' in sys.platform:
        installer = TeamServerLinuxInstaller() if is_teamserver() else ClientLinuxInstaller()
    elif 'win32' in sys.platform:
        installer = TeamServerWinInstaller() if is_teamserver() else ClientWinInstaller()
    elif 'darwin' in sys.platform:
        installer = TeamServerOSXInstaller() if is_teamserver() else ClientOSXInstaller()
    else:
        raise ValueError("Unsupported OS: {}".format(sys.platform))

    return installer
