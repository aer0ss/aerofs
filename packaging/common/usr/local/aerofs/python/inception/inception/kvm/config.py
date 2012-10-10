"""
Module to hold all utilities related to directly manipulating the configuration
on a KVM, i.e. changing things on the filesystem, etc.
"""

import os
import tempfile
import shutil
import socket
import subprocess
import inception.gen.kvm_pb2

#
# Linux system files
#

INTERFACE_FILE = '/etc/network/interfaces'
HOSTNAME_FILE = '/etc/hostname'
HOSTS_FILE = '/etc/hosts'

#
# Some utilities to help out with the functions below.
#

def replace_in_file(filename, pattern, subst):
    # Create temp file
    fh, abs_path = tempfile.mkstemp()

    new_file = open(abs_path, 'w')
    old_file = open(filename)

    for line in old_file:
        new_file.write(line.replace(pattern, subst))

    new_file.close()
    os.close(fh)
    old_file.close()

    shutil.move(abs_path, filename)

#
# Functions to facilitate configuration of the KVM, as well as access to configuration information
# (mainly hostname, network and dns configuration).
#

def configure_hostname(new_hostname):
    replace_in_file(HOSTS_FILE, socket.gethostname(), new_hostname)
    replace_in_file(HOSTNAME_FILE, socket.gethostname(), new_hostname)

    try:
        subprocess.check_output(['hostname', new_hostname])
    except subprocess.CalledProcessError, e:
        return False

    return True

def configure_network_dhcp(dns):
    fh, abs_path = tempfile.mkstemp()
    new_file = open(abs_path, 'w')

    new_file.write('auto lo\n')
    new_file.write('iface lo inet loopback\n')
    new_file.write('\n')
    new_file.write('auto eth0\n')
    new_file.write('iface eth0 inet dhcp\n')
    if len(dns) > 0:
        new_file.write('    dns-nameservers ' + dns + '\n')

    new_file.close()
    os.close(fh)

    shutil.move(abs_path, INTERFACE_FILE)
    return True

def configure_network_static(address, netmask, network, broadcast, gateway,
        dns):
    fh, abs_path = tempfile.mkstemp()
    new_file = open(abs_path, 'w')

    new_file.write('auto lo\n')
    new_file.write('iface lo inet loopback\n')
    new_file.write('\n')

    # Only address is required here.
    new_file.write('auto eth0\n')
    new_file.write('iface eth0 inet static\n')
    new_file.write('    address ' + address + '\n')
    if len(netmask) > 0:
        new_file.write('    netmask ' + netmask + '\n')
    if len(network) > 0:
        new_file.write('    network ' + network + '\n')
    if len(broadcast) > 0:
        new_file.write('    broadcast ' + broadcast + '\n')
    if len(gateway) > 0:
        new_file.write('    gateway ' + gateway + '\n')
    if len(dns) > 0:
        new_file.write('    dns-nameservers ' + dns + '\n')

    new_file.close()
    os.close(fh)

    shutil.move(abs_path, INTERFACE_FILE)
    return True

# Read the network interfaces file and return a GetStatusReply object with
# address, netmask, network, broadcast, getway and secondayDns (dns-namservers)
# set appropriately.
def read_etc_network_interfaces():
    reply = inception.gen.kvm_pb2.GetStatusReply()
    fh = open(INTERFACE_FILE)

    for line in fh:
        if 'static' in line:
            reply.networkType = inception.gen.kvm_pb2.GetStatusReply.STATIC
        elif 'dhcp' in line:
            reply.networkType = inception.gen.kvm_pb2.GetStatusReply.DHCP
        if 'address' in line:
            reply.address = line.partition('address')[2].strip()
        elif 'netmask' in line:
            reply.netmask = line.partition('netmask')[2].strip()
        elif 'network' in line:
            reply.network = line.partition('network')[2].strip()
        elif 'broadcast' in line:
            reply.broadcast = line.partition('broadcast')[2].strip()
        elif 'gateway' in line:
            reply.gateway = line.partition('gateway')[2].strip()
        elif 'dns-nameservers' in line:
            reply.dns = line.partition('dns-nameservers')[2].strip()

    return reply
