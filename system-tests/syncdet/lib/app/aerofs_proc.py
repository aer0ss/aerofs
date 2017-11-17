import commands
import os
import subprocess
import sys
import time
# SIGKILL not available on Windows.
if 'win32' not in sys.platform.lower():
    from signal import SIGKILL
from syncdet.case import background

from aerofs_common.param import POLLING_INTERVAL
from lib import ritual
from cfg import get_cfg, BaseLinuxCfg, BaseWin32Cfg, BaseOSXCfg


def wait_for_daemon():
    print 'waiting for daemon heartbeat...'
    ritual.wait_for_heartbeat()
    time.sleep(0.5)  # sleep another half-second for good measure. Call me superstitious.


def run_sh_and_check_output(*args):
    if 'linux' not in sys.platform:
        raise NotImplementedError("Can't run Linux-only program 'aerofs-sh'")
    if any(type(a) != str for a in args):
        raise ValueError("Args to aerofs-sh must be strings")
    proc = 'aerofs-sh'
    cmd = [os.path.join(get_cfg().get_approot(), proc)] + list(args)
    return subprocess.check_output(cmd)


def run_ui(*args):
    """ Run the GUI on windows or the CLI on Linux """
    if any(type(a) != str for a in args):
        raise ValueError("Args to aerofs-gui or aerofs-cli must be strings")
    _cfg = get_cfg()
    cmd = _cfg.get_ui_cmd() + list(args)
    key = _cfg.get_ui_name()
    if isinstance(_cfg, BaseWin32Cfg) and 'LOCALAPPDATA' not in os.environ:
        os.environ['LOCALAPPDATA'] = _cfg.get_local_appdata_path()
    p = background.start_process(cmd, key)
    wait_for_daemon()
    return p, key


def wait_for_all_to_die():
    get_process_killer().wait_for_aerofs_processes_to_die()

def stop_all():
    get_process_killer().kill_aerofs_processes()


#####                                        #####
### Platform-Dependent Process Killing Methods ###
#####                                        #####

def get_process_killer():
    if 'linux' in sys.platform:
        return LinuxProcessKiller()
    elif 'darwin' in sys.platform:
        return OSXProcessKiller()
    elif 'win32' in sys.platform:
        return Win32ProcessKiller()
    else:
        raise NotImplementedError("Unsupported OS: {}".format(sys.platform))


class BaseProcessKiller(object):

    def _get_aerofs_processes(self):
        raise NotImplementedError

    def kill_aerofs_processes(self):
        raise NotImplementedError

    def wait_for_aerofs_processes_to_die(self):
        print 'waiting for aerofs processes to die'
        while self._get_aerofs_processes():
            time.sleep(POLLING_INTERVAL)


class Win32ProcessKiller(BaseProcessKiller):

    def _get_aerofs_processes(self):
        """ Return a list of (pid, cmd) pairs. """
        # The CSV output is: Name,PID,SessionName,SessionNum,MemUsage
        csv_output = subprocess.check_output(['tasklist', '/FO', 'csv', '/NH']).strip()
        all_proc = [line.split(',') for line in csv_output.split('\r\n')]
        return [(int(l[1].strip('"')), l[0].strip('"')) for l in all_proc if 'aerofs' in l[0]]

    def kill_aerofs_processes(self):
        print 'preparing to kill aerofs processes...'
        # Kill processes in one call, rather than killing each individually by pid.
        # Otherwise, the gui may clean up the daemon and killing the daemon by pid throws
        # an exception
        # N.B. do this only when at least one process exists, or an exception will be thrown
        if self._get_aerofs_processes():
            subprocess.check_call(['taskkill', '/F', '/IM', 'aerofs*'])
        while self._get_aerofs_processes():
            time.sleep(POLLING_INTERVAL)


class UnixProcessKiller(BaseProcessKiller):

    def _get_ps_command(self):
        raise NotImplementedError

    def _filter(self, cmd):
        raise NotImplementedError

    def _get_aerofs_processes(self):
        """ Return a list of (pid, cmd) pairs. """
        ps_output = subprocess.check_output(self._get_ps_command()).strip()
        all_proc = [line.strip().split(' ', 1) for line in ps_output.split('\n')[1:]]
        return [(int(pid), cmd) for pid, cmd in all_proc if self._filter(cmd)]

    def kill_aerofs_processes(self):
        # This somewhat convoluted way of killing processes accomplishes two important things:
        # 1. processes will not be sent two SIGKILL signals
        # 2. processes spawned after the first iteration will still be killed (e.g. a daemon
        #    which is spawned just before a CLI is killed
        print 'preparing to kill aerofs processes...'
        killed = set()
        while True:
            alive = self._get_aerofs_processes()
            if not alive:
                return
            for pid, cmd in alive:
                if pid in killed:
                    continue
                print 'killing process {} {}'.format(pid, cmd)
                try:
                    os.kill(pid, SIGKILL)
                    killed.add(pid)
                except OSError:
                    print 'WARNING: could not send SIGKILL.'
            time.sleep(POLLING_INTERVAL)


class LinuxProcessKiller(UnixProcessKiller):

    def _get_ps_command(self):
        return ['/bin/ps', '-e', '-o', 'pid,cmd']

    def _filter(self, cmd):
        # N.B. filter for processes that match any subclass's approot. You want to kill any client
        # *and* teamserver processes before running a new client or teamserver. The code below
        # accomplishes this while keeping a sufficient level of abstraction.
        return any(cls().get_approot() in cmd for cls in BaseLinuxCfg.__subclasses__())



class OSXProcessKiller(UnixProcessKiller):

    def _get_ps_command(self):
        return ['/bin/ps', '-e', '-o', 'pid,command']

    def _filter(self, cmd):
        # N.B. filter for processes that match any subclass's approot. You want to kill any client
        # *and* teamserver processes before running a new client or teamserver. The code below
        # accomplishes this while keeping a sufficient level of abstraction.
        # On OSX we the UI does not actually live in the approt so we need to check for ui_name as well
        return any((cls().get_approot() in cmd or cls().get_ui_name() in cmd) for cls in BaseOSXCfg.__subclasses__())

