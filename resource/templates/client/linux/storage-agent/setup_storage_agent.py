#!/usr/bin/python
"""
The purpose of this python script is to setup and configure the Storage Agent.
The script accepts as input:
    2. A username(org admin username)
    3. Password for the given username
and proceeds to sign in with the AeroFS appliance. This script is also responsible
for generating the configuration db and starting the Storage Agent launcher.
It must ensure that before we launch the Storage Agent we have a cert, key and conf
db file.
"""
import argparse
import base64
import getpass
import hashlib
import netifaces
import os
import platform
import pwd
import re
import requests
import shutil
import socket
import subprocess
import sys
import time
import uuid
import warnings
warnings.filterwarnings('ignore')

from aerofs_common.exception import ExceptionReply
from aerofs_sp.connection import SyncConnectionService
from aerofs_sp.gen.common_pb2 import PBException
from aerofs_sp.gen.sp_pb2 import RegisterDeviceCall
from aerofs_sp.gen.sp_pb2 import SPServiceRpcStub
from aerofs_sp.param import SP_PROTO_VERSION
from OpenSSL import crypto, SSL

device_name = socket.gethostname().split(".")[0]
owner_uid = os.getuid()
default_rtroot = os.path.join(os.path.expanduser("~"), ".aerofs-storage-agent")


def get_storage_dict(storage_type, storage_properties=None):
    storage_dict = {}
    if storage_type.lower() == 'local':
        storage_dict['storage_type'] = 'LOCAL'

    elif storage_type.lower() == 's3':
        storage_dict['storage_type'] = 'S3'
        storage_dict['s3_bucket_id'] = storage_properties.get('s3_bucket_id', None) if storage_properties is not None \
            else raw_input("S3 bucket id: ")
        storage_dict['s3_access_key'] = storage_properties.get('s3_access_key', None) if storage_properties is not None \
            else raw_input("S3 access key: ")
        storage_dict['s3_secret_key'] = storage_properties.get('s3_secret_key', None) if storage_properties is not None \
            else getpass.getpass("S3 secret key: ")
        passphrase = storage_properties.get('remote_storage_encryption_password', None) if storage_properties is not None \
            else getpass.getpass("S3 passphrase: ")
        storage_dict['remote_storage_encryption_password'] = passphrase if passphrase is None or '' else base64.b64encode(passphrase)

    elif storage_type.lower() == 'swift':
        storage_dict['storage_type'] = 'SWIFT'
        storage_dict['swift_auth_mode'] = storage_properties.get('swift_auth_mode', None) if storage_properties is not None \
            else raw_input("Swift auth mode: ")
        storage_dict['swift_url'] = storage_properties.get('swift_url', None) if storage_properties is not None \
            else raw_input("Swift url: ")
        storage_dict['swift_username'] = storage_properties.get('swift_username', None) if storage_properties is not None \
            else raw_input("Swift username: ")
        storage_dict['swift_password'] = storage_properties.get('swift_password', None) if storage_properties is not None \
            else getpass.getpass("Swift pwd: ")
        storage_dict['swift_container'] = storage_properties.get('swift_container', None) if storage_properties is not None \
            else (raw_input("Swift container: ") or "aerofs")
        if "keystone" == swift_auth_mode:
            storage_dict['swift_tenant_id'] = storage_properties.get('swift_tenant_id', None) if storage_properties is not None \
                else raw_input("Swift tenant id: ")
            storage_dict['swift_tenant_name'] = storage_properties.get('swift_tenant_name', None) if storage_properties is not None \
                else raw_input("Swift tenant name: ")
        passphrase = storage_properties.get('remote_storage_encryption_password', None) if storage_properties is not None \
            else getpass.getpass("Swift passphrase: ")
        storage_dict['remote_storage_encryption_password'] = passphrase if passphrase is None or '' else base64.b64encode(passphrase)

    return storage_dict


def _parse_input_args(argv):
    global owner_uid
    global rtroot

    parser = argparse.ArgumentParser()
    parser.add_argument('-u', '--username', action="store", default=None, help="Username of AeroFS account")
    parser.add_argument('-p', '--password', action="store", default=None, help="Password of AeroFS account")
    parser.add_argument('-r', '--root_anchor', action="store", default=None, help="Absolute AeroFS directory location")
    parser.add_argument('-l', '--rtroot', action="store", default=None, help="Absolute rtroot location")
    parser.add_argument('-a', '--appliance_address', action="store", default=None, help="Address of your AeroFS appliance(only hostname).")
    parser.add_argument('-c', '--sa_config_dir', action="store", default=None, help="Directory where to install the storage-agent properties")
    parser.add_argument('-s', '--storage_options', action="store", default=None, help="Storage type options. Options: Local|S3|Swift")
    parser.add_argument('-f', '--unattended_setup_file', action="store", default=None, help="Setup file containing info to configure and start AeroFS")

    input_args = parser.parse_args(argv)

    username, password, root_anchor, rtroot, appliance_addr, sa_config_dir, storage_type = \
        input_args.username, input_args.password, input_args.root_anchor, input_args.rtroot, input_args.appliance_address, input_args.sa_config_dir, input_args.storage_options

    storage_dict = {}
    if input_args.unattended_setup_file is not None:
        properties = dict(re.split('[ =:]', line.strip()) for line in open(input_args.unattended_setup_file))

        username = username if username is not None else properties.get('userid', None)
        password = password if password is not None else properties.get('password', None)
        root_anchor = os.path.join(root_anchor if root_anchor is not None else properties.get('root', "{}".format(os.path.expanduser("~"))), \
                    "AeroFS")
        appliance_addr = appliance_addr if appliance_addr is not None else properties.get('appliance', None)
        rtroot = rtroot if rtroot is not None else properties.get('rtroot', os.path.join(os.path.dirname(root_anchor), ".aerofs-storage-agent"))
        sa_config_dir = sa_config_dir if sa_config_dir is not None else properties.get('conf', rtroot)
        storage_type = properties.get('storage_type', None)
        storage_dict = get_storage_dict(storage_type, properties)

    else:
        if username is None:
            username = raw_input("AeroFS username: ")
        if password is None:
            password = getpass.getpass('AeroFS password: ')
        if root_anchor is None:
            root_anchor = raw_input("Absolute location of your AeroFS directory.[{}]: ".format(os.path.expanduser("~")))
            root_anchor = '{0}/AeroFS'.format(os.path.expanduser("~")) if root_anchor == '' else root_anchor + "/AeroFS"
        else:
            root_anchor = os.path.join(root_anchor, "AeroFS")
        if rtroot is None:
            rtroot = default_rtroot
        if appliance_addr is None:
            appliance_addr = raw_input("Address of your AeroFS appliance(only hostname): ")
        if sa_config_dir is None:
            sa_config_dir = raw_input("Absolute location of where to install the storage-agent config properties.[{}]: ".format(rtroot))
            sa_config_dir = rtroot if sa_config_dir == '' else sa_config_dir

        if storage_type is None:
            print ("The following storage options are available:")
            print ("[1] Store compressed local files on disk (Default).")
            print ("[2] Store files on Amazon S3")
            print ("[3] Store  files on OpenStack Swift")
            storage_type = raw_input("Choose your option: ") or "1"
            if int(storage_type) == 1:
                storage_type = "LOCAL"
            elif int(storage_type) == 2:
                storage_type = "S3"
            elif int(storage_type) == 3:
                storage_type = "SWIFT"

        if not storage_dict:
           storage_dict = get_storage_dict(storage_type)

        if owner_uid == 0:
            print("Looks like you are running this script as root. Would you like to create all AeroFS specific folders as: ")
            print("[1] {} (Default)".format(os.environ.get('SUDO_USER')))
            print("[2] root")
            owner = raw_input("Choose your option: ") or "1"
            if int(owner) == 1:
                owner_uid = int(os.environ.get('SUDO_UID'))


    return username, password, root_anchor, rtroot, appliance_addr, sa_config_dir, storage_dict


##### taken from Drew's secutil.py
alpha = [c for c in "abcdefghijklmnop"]
def alphabet_encode(bytestring):
    if not isinstance(bytestring, bytes):
        raise ValueError("bytestring must be a byte array")
    retval = []
    for c in bytestring:
        b = ord(c)
        retval.append(alpha[(b >> 4) & 0xf])
        retval.append(alpha[b & 0xf])
    return "".join(retval)

def magichash(username, did):
    if not isinstance(username, unicode):
        raise ValueError("username must be a unicode string")
    if not isinstance(did, bytes):
        raise ValueError("did must be a byte array")
    h = hashlib.sha256()
    h.update(username.encode('utf-8'))
    h.update(did)
    return h.digest()
##### end of secutil stuff


def _create_rtroot_with_perms(rtroot):
    if not os.path.exists(rtroot):
        # N.B. This is used instead of os.makedirs because we want to be to create
        # all intermediate directories with the logged in user as the owner and not as root
        # if script is being run with sudo. One could use os.makedirs + os.chown but then
        # one would have to keep track of all intermediate directories that may have been created
        # and chown every single one of those.
        cmd = ['mkdir', '-p', rtroot]
        subprocess.check_output(cmd, preexec_fn=exec_as_owner(owner_uid, pwd.getpwuid(owner_uid).pw_gid))

    cmd = ['touch', os.path.join(rtroot, "polaris")]
    subprocess.check_output(cmd, preexec_fn=exec_as_owner(owner_uid, pwd.getpwuid(owner_uid).pw_gid))

def _create_sa_conf_file(sp, username, root_anchor, rtroot, did, sa_user_id, storage_dict, sa_conf_dir):
    cfg_data = {
        'user_id': sa_user_id,
        'device_id': did.hex,
        'root': os.path.abspath(root_anchor),
        'contact_email': username,
        'rest_service': str(True),
    }
    cfg_data.update(storage_dict)

    try:
        with open(os.path.join(sa_conf_dir, "storage_agent.conf"), 'w') as f:
            for key, val in cfg_data.items():
                f.write(key + " " + val)
                f.write("\n")
    except IOError as e:
        if e.errno == 13:
            print ("Looks like you trying to create the storage-agent configuration file under {0},\
                    however you don't have the necessary permissions to write to {0}".format(sa_conf_dir))
            sys.exit()
        else:
            raise e


def _sign_in_user(rtroot, username, password, appliance_addr):
    con = SyncConnectionService("https://{}:4433/sp/".format(appliance_addr), SP_PROTO_VERSION)
    sp = SPServiceRpcStub(con)
    while True:
        try:
            reply = sp.credential_sign_in(username, password)
            break
        except ExceptionReply as e:
            if e.get_type() == PBException.BAD_CREDENTIAL:
                print ("Wrong username or password. Try again")
                username = raw_input("AeroFS username: ")
                password = getpass.getpass('AeroFS password: ')
                continue
        except requests.exceptions.ConnectionError:
            print ("Unable to connect to your AeroFS appliance at {}. Please make sure that your appliance is up and reachable. Exiting now...".format(appliance_addr))
            sys.exit()

    if reply.HasField('need_second_factor') and reply.need_second_factor:
        while(True):
            try:
                second_factor = int(raw_input("Enter your second factor:"))
            except ValueError:
                print ("Please enter a valid 6 digit integer code.")
                continue
            if len(str(second_factor)) != 6:
                print ("Please enter a valid 6 digit interger code.")
                continue
            try:
                sp.provide_second_factor(second_factor)
            except exception.ExceptionReply as e:
                if e.get_type() == PBException.SECOND_FACTOR_REQUIRED:
                    print ("Incorrect authentication code entered. Try again please!")
                    continue
                if e.get_type() == PBException.RATE_LIMIT_REQUIRED:
                    print ("Rate limit exceeded. Please wait a minute before retrying. Sleeping for a minute")
                    time.sleep(60)
                    continue
            break
    return sp


def _sanitize_required_folder_locations(folder):
    if folder is None or len(folder) == 0:
        raise Exception("No location provided for {}".format(folder))
    if os.path.isfile(folder):
        raise Exception("A file {} already exists.".format(folder))


def _create_root_anchor(root_anchor):
    if os.path.exists(root_anchor):
        shutil.rmtree(root_anchor)
    cmd = ['mkdir', '-p', root_anchor]
    subprocess.check_output(cmd, preexec_fn=exec_as_owner(owner_uid, pwd.getpwuid(owner_uid).pw_gid))

    # check perm
    if not os.access(root_anchor, os.W_OK|os.R_OK):
        raise Exception("User doesn't have permissions to access to AeroFS directory")


def exec_as_owner(user_uid, user_gid):
    def result():
        os.setgid(user_gid)
        os.setuid(user_uid)
    return result


def _create_CSR(sa_user_id, did, key):
    csr_req = crypto.X509Req()
    cname = unicode(alphabet_encode(magichash(unicode(sa_user_id), did.bytes)))

    csr_req.get_subject().CN = cname
    csr_req.get_subject().organizationalUnitName = "na"
    csr_req.get_subject().organizationName = "aerofs.com"
    csr_req.get_subject().stateOrProvinceName = "CA"
    csr_req.get_subject().countryName = "US"
    csr_req.get_subject().localityName = "SF"

    csr_req.set_pubkey(key)

    csr_req.sign(key, "sha1")
    return csr_req


def _write_cert_to_file(rtroot, cert):
    # Write cert
    cert_file = os.path.join(rtroot, "cert")
    f = open(os.path.join(cert_file), 'w')
    f.write(str(cert))
    f.close()
    os.chown(cert_file, owner_uid, pwd.getpwuid(owner_uid).pw_gid)


def _write_key_to_file(rtroot, key):
    # Write temp key
    key_file = os.path.join(rtroot, "key.pem")
    temp_file = os.path.join(rtroot, "key_temp")
    f = open(temp_file, 'w')
    f.write(crypto.dump_privatekey(crypto.FILETYPE_PEM, key))
    f.close()
    # Hello, welcome to hack city. It seems like pyOpenSSL.crypto.Pkey generates
    # pcks1 format private key in OSX and pcks8 format private key in Linux. While storage-agent
    # is not expected to ship for OSX, to avoid platform dependencies, convert all
    # keys explicitly to pkcs8 format via the command line through openssl cmd.
    # openssl takes in as input a traditional format(pcks1) key file and outputs a pkcs8 format
    # private key file. Hence the above creation of a temp file(and subsequent deletion) .
    pkcs_cmd = "openssl pkcs8 -topk8 -in {0} -outform pem -out {1} -nocrypt".format(temp_file, key_file)
    res = os.system(pkcs_cmd)
    assert res == 0 and os.path.exists(key_file)
    os.remove(temp_file)
    os.chown(key_file, owner_uid, pwd.getpwuid(owner_uid).pw_gid)


def _get_network_interfaces():
    pb_interfaces = []
    for interface in netifaces.interfaces():
        pb_interface = RegisterDeviceCall.Interface()
        pb_interface.name = interface
        try:
            pb_interface.mac = netifaces.ifaddresses(interface)[netifaces.AF_LINK][0]['addr']
        except KeyError:
            pb_interface.mac = ""
        try:
            for ifaddrs in netifaces.ifaddresses(interface)[netifaces.AF_INET]:
                pb_interface.ips.append(ifaddrs['addr'])
        except KeyError:
            # Do nothing.
            pass
        pb_interfaces.append(pb_interface)
    return pb_interfaces


def _install_sa_user_impl(sp, rtroot):
    try:
        sa_user_id = sp.get_team_server_user_id().id
    except ExceptionReply as e:
        if e.get_type() == PBException.NO_PERM:
            print ("You don't have permissions to install a storage agent. Please contant your organization admin.")
            sys.exit()

    # Generate new RSA keypair
    key = crypto.PKey()
    key.generate_key(crypto.TYPE_RSA, 2048)

    # Generate did
    did = uuid.uuid4()

    # Get CSR
    csr_req = _create_CSR(sa_user_id, did, key)

    fullOSName = ' '.join(platform.linux_distribution())

    pb_network_interfaces = _get_network_interfaces()

    # DER enconding
    der_encoded_req = crypto.dump_certificate_request(crypto.FILETYPE_ASN1, csr_req)

    cert = sp.register_team_server_device(did.bytes, der_encoded_req, platform.system(), fullOSName, device_name, pb_network_interfaces).cert

    _write_key_to_file(rtroot, key)
    _write_cert_to_file(rtroot, cert)

    return did, sa_user_id


def _install_user(sp, rtroot, root_anchor):
    assert sp is not None

    _create_root_anchor(root_anchor)
    return _install_sa_user_impl(sp, rtroot)


def _download_site_config(appliance_addr):
    site_config_file = os.path.join(rtroot, 'site-config.properties')
    client_config_url = 'https://{}:4433/config/client'.format(appliance_addr)
    r = requests.get(client_config_url, verify=False)
    with open(site_config_file, 'w') as f:
        f.write(r.text)


def main(argv):
    username, password, root_anchor, rtroot, appliance_addr, sa_conf_dir, storage_dict = _parse_input_args(argv)
    print ("=== Validated input ===")

    _sanitize_required_folder_locations(rtroot)
    _sanitize_required_folder_locations(root_anchor)

    _create_rtroot_with_perms(rtroot)

    sp = _sign_in_user(rtroot, username, password, appliance_addr)
    print ("=== Signed in as {} ===".format(username))

    did, sa_user_id = _install_user(sp, rtroot, root_anchor)
    print ("=== Installed storage agent as user {} ===".format(username))

    _create_sa_conf_file(sp, username, root_anchor, rtroot, did, sa_user_id, storage_dict, sa_conf_dir)
    print ("=== Finished configuring storage agent for user {} ===".format(username))

    _download_site_config(appliance_addr)
    print ("***Successfully completed installing storage agent. Ready to run***")


if __name__ == "__main__":
    main(sys.argv[1:])
