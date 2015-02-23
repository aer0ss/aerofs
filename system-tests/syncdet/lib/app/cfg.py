"""
This module provides utility functions that manipulate the AeroFS
application as a whole. It also provides functions to retrieve AeroFS
configuration and parameters.

"""
import os.path
import platform
import sys
import uuid
import getpass

from syncdet import case

from conf_reader import ConfReader


#####              #####
### Public Functions ###
#####              #####

# This returns the native OS homedir. On Win32, this returns a path to the WINDOWS homedir
def get_native_homedir():
    if 'linux' in sys.platform:
        return os.path.expanduser('~')
    elif 'darwin' in sys.platform:
        return os.path.expanduser('~')
    elif 'win32' in sys.platform:
        return os.environ["USERPROFILE"]
    else:
        raise Exception("Can't get homedir for OS: " + sys.platform)


def is_teamserver():
    actor = case.local_actor()
    details = getattr(actor, 'details', {})
    return details.get('team_server', False)


def get_cfg():
    if 'linux' in sys.platform:
        cfg = TeamServerLinuxCfg() if is_teamserver() else ClientLinuxCfg()
    elif 'darwin' in sys.platform:
        cfg = TeamServerOSXCfg() if is_teamserver() else ClientOSXCfg()
    elif 'win32' in sys.platform:
        cfg = TeamServerWin32Cfg() if is_teamserver() else ClientWin32Cfg()
    else:
        raise ValueError("Unsupported OS: {}".format(sys.platform))
    return cfg


#####         #####
### Base Config ###
#####         #####

class BaseCfg(object):

    def get_approot(self):
        raise NotImplementedError

    def get_rtroot(self):
        raise NotImplementedError

    def get_root_anchor(self):
        raise NotImplementedError

    def get_ui_name(self):
        raise NotImplementedError

    def get_ui_cmd(self):
        raise NotImplementedError

    def _conf_db(self):
        """
        @return a new ConfReader object which wraps the keys of the conf sqlite db
        Conf is no longer cached to test changes in Cfg (e.g. root anchor changes)
        """
        return ConfReader(os.path.join(self.get_rtroot(), ConfReader.filename()))

    def user(self):
        """ @return the user-name for the local AeroFS client """
        return self._conf_db().user_id

    def get_ritual_path(self):
        raise NotImplementedError

    def did(self):
        """ @return the uuid representation of the AeroFS device ID for the local actor """
        return uuid.UUID(self._conf_db().device_id)


#####          #####
### Linux Config ###
#####          #####

class BaseLinuxCfg(BaseCfg):

    def get_root_anchor(self):
        return os.path.expanduser(self._conf_db().root)

    def get_ui_cmd(self):
        return [os.path.join(self.get_approot(), self.get_ui_name())]

    def get_ritual_path(self):
        return "{0}/{1}".format(self.get_rtroot(), "ritual.sock")

    def get_ritual_notification_path(self):
        return "{0}/{1}".format(self.get_rtroot(), "rns.sock")


class ClientLinuxCfg(BaseLinuxCfg):

    def get_approot(self):
        return os.path.join(os.path.expanduser('~'), '.aerofs-bin')

    def get_rtroot(self):
        return os.path.join(os.path.expanduser('~'), '.aerofs')

    def get_ui_name(self):
        return 'aerofs-cli'


class TeamServerLinuxCfg(BaseLinuxCfg):

    def get_approot(self):
        return os.path.join(os.path.expanduser('~'), '.aerofsts-bin')

    def get_rtroot(self):
        return os.path.join(os.path.expanduser('~'), '.aerofsts')

    def get_ui_name(self):
        return 'aerofsts-cli'


#####          #####
### OSX Config ###
#####          #####

class BaseOSXCfg(BaseCfg):

    def get_root_anchor(self):
        return os.path.expanduser(self._conf_db().root)

    def _app_name(self):
        raise NotImplementedError

    def _app_path(self):
        # NB: in a real install /Applications is used but to avoid perm issues syncdet uses ~/Applications
        return os.path.join(os.path.expanduser('~'), 'Applications', self._app_name() + '.app')

    def get_approot(self):
        return os.path.join(self._app_path(), 'Contents', 'Resources', 'Java')

    def get_rtroot(self):
        return os.path.join(os.path.expanduser('~'), 'Library', 'Application Support', self._app_name())

    def get_ui_name(self):
        return self._app_name() + 'Client'

    def get_ui_cmd(self):
        return [os.path.join(self._app_path(), 'Contents', 'MacOS', self.get_ui_name()), self.get_rtroot(), 'cli']

    def get_ritual_path(self):
        return "{0}/{1}".format(self.get_rtroot(), "ritual.sock")

    def get_ritual_notification_path(self):
        return "{0}/{1}".format(self.get_rtroot(), "rns.sock")


class ClientOSXCfg(BaseOSXCfg):

    def _app_name(self):
        return 'AeroFS'


class TeamServerOSXCfg(BaseOSXCfg):

    def _app_name(self):
        return 'AeroFSTeamServer'


#####           #####
### Win32 Config ###
#####           #####

class BaseWin32Cfg(BaseCfg):

    def get_root_anchor(self):
        root_path = os.path.expanduser(self._conf_db().root)
        return root_path

    def _get_appdata_path(self):
        return os.path.join(get_native_homedir(), 'AppData')

    def get_local_appdata_path(self):
        # The rtroot lives under the local appdata path.
        # Windows XP is annoying and uses the folder
        # "%USERPROFILE%\Local Settings\Application Data" instead of
        # "%USERPROFILE%\AppData\Local", so we have to special-case it here.
        if platform.win32_ver()[0] == "XP":
            return os.path.join(get_native_homedir(), "Local Settings", "Application Data")
        return os.path.join(self._get_appdata_path(), 'Local')

    def get_roaming_appdata_path(self):
        # The approot lives under the roaming appdata path.
        # Windows XP is annoying and uses the folder
        # "%USERPROFILE%\Application Data" instead of
        # "%USERPROFILE%\AppData\Roaming", so we have to special-case it here.
        if platform.win32_ver()[0] == "XP":
            return os.path.join(get_native_homedir(), "Application Data")
        return os.path.join(self._get_appdata_path(), 'Roaming')

    def get_ui_cmd(self):
        return [os.path.join(self.get_approot(), self.get_ui_name())]

    def get_ritual_path(self):
        file_name = "{0}_ritual_{1}".format("cyg_server", "ts" if is_teamserver() else "single")
        return r'{0}\{1}'.format(r'\\.\pipe', file_name)

    def get_ritual_notification_path(self):
        file_name = "{0}_rns_{1}".format("cyg_server", "ts" if is_teamserver() else "single")
        return r'{0}\{1}'.format(r'\\.\pipe', file_name)



class ClientWin32Cfg(BaseWin32Cfg):

    def get_approot(self):
        return os.path.join(self.get_roaming_appdata_path(), 'AeroFSExec')

    def get_rtroot(self):
        return os.path.join(self.get_local_appdata_path(), 'AeroFS')

    def get_ui_name(self):
        return 'aerofs.exe'


class TeamServerWin32Cfg(BaseWin32Cfg):

    def get_approot(self):
        return os.path.join(self.get_roaming_appdata_path(), 'AeroFSTeamServerExec')

    def get_rtroot(self):
        return os.path.join(self.get_local_appdata_path(), 'AeroFSTeamServer')

    def get_ui_name(self):
        return 'aerofsts.exe'

