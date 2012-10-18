######################################################################
#
# Utility functions to start and stop the AeroFS gui on each of
# the OS platforms.
#
######################################################################
import sys
import os
import subprocess
import urllib2
import re

from lib import app

class _PackagedAeroFSClient(object):
    def __init__(self, get_gui_pids_command, get_daemon_pids_command):
        self._get_gui_pids_command = get_gui_pids_command
        self._get_daemon_pids_command = get_daemon_pids_command

    def _get_aerofs_process_pids(self, find_pid_command):
        """Use the given command 'find_pid_command' to search for AeroFS processes on the
        Actor's system.

        @return a list of PIDs

        """
        # We manually check the output here because Python2.6 does not support
        # the subprocess.check_output() method, and our actors only support 2.6.
        p = subprocess.Popen(find_pid_command, shell=True, stdout=subprocess.PIPE)
        stdoutdata, _ = p.communicate()
        output_lines = stdoutdata.strip().split('\n')
        if p.returncode != 0:
            raise Exception("command failed with error {0}".format(p.returncode))
        return [int(line) for line in output_lines]

    def get_aerofs_gui_pids(self):
        """Get the PIDs of any AeroFS GUI processes running on the actor, or throw
        an exception if none are found.

        @return the PIDs

        """
        try:
            return self._get_aerofs_process_pids(self._get_gui_pids_command)
        except Exception as e:
            raise Exception("aerofs gui is not running: {0}".format(e))

    def get_aerofs_daemon_pids(self):
        """Get the PIDs of any AeroFS daemon processes running on the actor, or
        throw an exception if none are found.

        @return the PIDs

        """
        try:
            return self._get_aerofs_process_pids(self._get_daemon_pids_command)
        except Exception as e:
            raise Exception("aerofs daemon is not running: {0}".format(e))

    def stop_aerofs(self):
        """Stops the AeroFS GUI first, then the daemon. This order must be preserved
        because the GUI will respawn any killed daemons.

        """
        try:
            self._stop_aerofs_gui()
            self._stop_aerofs_daemon()
        except subprocess.CalledProcessError as e:
            raise Exception("no aerofs process exists or failed to kill the process: {0}".format(e))

    def is_aerofs_running(self):
        """ Check whether the AeroFS client and daemon are running on the actor. """
        # Look for the gui and daemon processes
        try:
            if len(self.get_aerofs_gui_pids()) == 0:
                return False

            if len(self.get_aerofs_daemon_pids()) == 0:
                return False

            return True
        except Exception:
            return False

    def versioned_approot_path(self):
        """Return the path to the AeroFS versioned approot directory. This is the same path
        as the approot directory on OSX and Linux, but on Windows it is a sub-directory of the
        approot directory.

        """
        return app.app_root_path()

    def version_file_path(self):
        """Returns the path to the version file located in the AeroFS
        installation directory.

        """
        return os.path.join(self.versioned_approot_path(), "version")

    def current_version_url(self):
        """Return the URL where the current AeroFS release version number is located."""
        if os.path.exists(os.path.join(app.app_root_path(), "stg")):
            return "https://nocache.client.stg.aerofs.com/current.ver"
        else:
            return "https://nocache.client.aerofs.com/current.ver"

class _NixClient(_PackagedAeroFSClient):
    def _kill(self, pids):
        """Kill the processes identified in the list 'pids'. Only compatible with *nix systems."""
        map(subprocess.check_call, [ ('kill', '-9', str(pid)) for pid in pids ])

    def _stop_aerofs_gui(self):
        self._kill(self.get_aerofs_gui_pids())

    def _stop_aerofs_daemon(self):
        self._kill(self.get_aerofs_daemon_pids())

class _LinuxClient(_NixClient):
    AEROFS_GET_PIDS_TEMPLATE = "ps -e -o pid,user,command | grep {0} | grep -v grep | awk '{{ print $1 }}'"

    def __init__(self):
        get_gui_command = self.AEROFS_GET_PIDS_TEMPLATE.format("aerofs.jar")
        get_daemon_command = self.AEROFS_GET_PIDS_TEMPLATE.format("aerofsd")
        super(_LinuxClient, self).__init__(get_gui_command, get_daemon_command)

    def start_aerofs(self):
        with open(os.devnull, 'w') as dev_null:
            aerofs_gui_path = os.path.join(app.app_root_path(), "aerofs-gui")

            # The linux client needs a DISPLAY set in order to launch with a GUI
            env = {}
            env.update(os.environ)
            env.update({"DISPLAY": ":0.0"})

            subprocess.check_call([aerofs_gui_path], env=env,
                    stderr=subprocess.STDOUT, stdout=dev_null)

class _OSXClient(_NixClient):
    AEROFS_GET_PIDS_TEMPLATE = "ps -e -o pid,user,command | grep {0} | grep -v grep | awk '{{ print $1 }}'"

    def __init__(self):
        get_gui_command = self.AEROFS_GET_PIDS_TEMPLATE.format("AeroFSClient")
        get_daemon_command = self.AEROFS_GET_PIDS_TEMPLATE.format("aerofsd")
        super(_OSXClient, self).__init__(get_gui_command, get_daemon_command)

    def start_aerofs(self):
        with open(os.devnull, 'w') as dev_null:
            # We must use the 'open' command on OSX to get the App to run in the
            # background. This means we need to point 'open' at the AeroFS.app
            # folder, which is not specified in any path, but is derived from
            # app_root.
            aerofs_gui_path = os.path.join(app.app_root_path(), "../../../.")
            aerofs_gui_path = os.path.normpath(aerofs_gui_path)
            subprocess.check_call(['open', aerofs_gui_path],
                    stderr=subprocess.STDOUT, stdout=dev_null)

class _WindowsClient(_PackagedAeroFSClient):
    AEROFS_GET_PIDS_TEMPLATE = "tasklist /fi \"IMAGENAME eq {0}\" /fo TABLE /nh | awk '{{ print $2 }}'"

    def __init__(self):
        get_gui_command = self.AEROFS_GET_PIDS_TEMPLATE.format("aerofs.exe")
        get_daemon_command = self.AEROFS_GET_PIDS_TEMPLATE.format("aerofsd.exe")
        super(_WindowsClient, self).__init__(get_gui_command, get_daemon_command)

    def start_aerofs(self):
        aerofs_gui_path = os.path.join(app.app_root_path(), "aerofs.exe")

        # The remote Windows actor requires a small server running on the
        # same machine to launch AeroFS. The reason for this is that starting
        # from Windows Vista, Windows services can not spawn processes
        # that have a GUI. SyncDET uses SSH to run tests on an
        # actor and the sshd daemon is a Windows service. That means
        # we can not launch AeroFS directly from the test. We need
        # a process already running in the Windows GUI environment
        # to launch AeroFS for us.
        #
        # There is a python server in tools/windows_launch_server.py,
        # the path being relative to this SyncDET deploy directory.
        # The server must be run from the physical Windows machine
        # once it has been deployed via syncdet.
        try:
            urllib2.urlopen("http://localhost:8000/{0}".format(aerofs_gui_path))
        except urllib2.HTTPError as e:
            raise e
        except urllib2.URLError as e:
            raise Exception("error: {0}\nthe Windows AeroFS launch server might not be\n"
                            "running. Please start it. It is located on the remote\n"
                            "actor, in the SyncDET deploy directory, under tools.\n"
                            .format(e))

    def _stop_aerofs_gui(self):
        subprocess.check_call(["taskkill", "/f", "/im", "aerofs.exe"])

    def _stop_aerofs_daemon(self):
        subprocess.check_call(["taskkill", "/f", "/im", "aerofsd.exe"])

    def versioned_approot_path(self):
        # Windows uses a versioned install directory, so we need to read in the
        # version directory from the aerofs.ini file
        aerofs_ini = os.path.join(app.app_root_path(), "aerofs.ini")

        with open(aerofs_ini, 'r') as f:
            # Search for the version directory string
            match = re.search("(v_[.0-9]+)", f.read())
            if match:
                # If it exists, create the path to the directory
                return os.path.join(app.app_root_path(), match.group(0))
            else:
                # This string may not exist if the installed AeroFS version
                # is from before the versioned installation directories
                # were introduced.
                return super(_WindowsClient, self).versioned_approot_path()

def _createPlatformSpecificClient():
    if "linux" in sys.platform:
        return _LinuxClient()
    elif "darwin" in sys.platform:
        return _OSXClient()
    elif "cygwin" in sys.platform:
        return _WindowsClient()
    else:
        raise Exception("unsupported platform: {0}".format(sys.platform))

_client = _createPlatformSpecificClient()

def instance():
    return _client
