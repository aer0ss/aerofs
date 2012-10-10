"""
VM Management Functions
"""

import subprocess
import os

def get_kvm_domain_list(kvm_img_dir):
    """
    Get a list of kvm images currently in the KVM image directory.
    """

    domain_list = []

    for root, dirs, files in os.walk(kvm_img_dir):
        for f in files:
            # A little bit of string parsing happening here.
            domain = f.partition('.')

            if len(domain) == 3 and domain[2] == 'img':
                domain_list.append(domain[0])

    return domain_list

class KernelVirtualMachine(object):
    """
    Access information about a kernel virtual machine, and configure autostart.
    """

    def __init__(self, autostart_dir, domain):
        self._autostart_dir = autostart_dir
        self._domain = domain

    def enable_service(self):
        try:
            subprocess.check_output(['virsh', 'autostart', str(self._domain)])
        except subprocess.CalledProcessError, e:
            return e.returncode

        if self.is_running() == False:
            try:
                subprocess.check_output(['virsh', 'start', str(self._domain)])
            except subprocess.CalledProcessError, e:
                return e.returncode

        return 0

    def disable_service(self):
        try:
            subprocess.check_output(['virsh', 'autostart', str(self._domain), '--disable'])
        except subprocess.CalledProcessError, e:
            return e.returncode

        if self.is_running():
            try:
                subprocess.check_output(['virsh', 'destroy', str(self._domain)])
            except subprocess.CalledProcessError, e:
                return e.returncode

        return 0

    def is_autostart_enabled(self):
        # When autostart is enabled, this file is created. Unfortunately there
        # is no command line way to check this.
        return os.path.exists(self._autostart_dir + '/' + self._domain + '.xml')

    def is_running(self):
        out = subprocess.check_output(['virsh', 'domstate', self._domain])
        return out == 'running\n\n'
