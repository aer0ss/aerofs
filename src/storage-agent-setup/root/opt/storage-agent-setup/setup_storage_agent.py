#!/usr/bin/python
"""
The purpose of this python script is to configure the Storage Agent.
The script accepts as input: The path (relative or absolute) to a storage agent config bundle
and proceeds to register a storage agent with the AeroFS appliance.
This script is also responsible for generating the configuration db.
It must ensure that before we launch the Storage Agent we have a cert, key and conf db file.
"""
import base64
import hashlib
import os
import platform
import pwd
import shutil
import sys
import uuid

from OpenSSL import crypto
from aerofs_sa.config import SITE_CONFIG_FILENAME, bundle_reader
from aerofs_sp.connection import SyncConnectionService
from aerofs_sp.gen.sp_pb2 import RegisterDeviceCall, SPServiceRpcStub
from aerofs_sp.gen.common_pb2 import PBException
from aerofs_sp.param import SP_PROTO_VERSION
from aerofs_common.exception import ExceptionReply

owner_uid = os.getuid()


def get_storage_dict(storage_properties):
    storage_type = storage_properties['storage_type']
    storage_dict = {}
    if storage_type.lower() == 'local':
        storage_dict['storage_type'] = 'LOCAL'

    elif storage_type.lower() == 's3':
        storage_dict['storage_type'] = 'S3'
        storage_dict['s3_bucket_id'] = storage_properties['s3_bucket_id']
        storage_dict['s3_access_key'] = storage_properties['s3_access_key']
        storage_dict['s3_secret_key'] = storage_properties['s3_secret_key']
        passphrase = storage_properties.get('s3_encryption_password', "")
        storage_dict['remote_storage_encryption_password'] = passphrase if passphrase else base64.b64encode(passphrase)

    elif storage_type.lower() == 'swift':
        storage_dict['storage_type'] = 'SWIFT'
        storage_dict['swift_auth_mode'] = storage_properties['swift_auth_mode']
        storage_dict['swift_url'] = storage_properties['swift_url']
        storage_dict['swift_username'] = storage_properties['swift_username']
        storage_dict['swift_password'] = storage_properties['swift_password']
        storage_dict['swift_container'] = storage_properties['swift_container']
        passphrase = storage_properties.get('swift_encryption_password', "")
        storage_dict['remote_storage_encryption_password'] = passphrase if passphrase else base64.b64encode(passphrase)
        if "keystone" == storage_dict['swift_auth_mode']:
            storage_dict['swift_tenant_id'] = storage_properties['swift_tenant_id']
            storage_dict['swift_tenant_name'] = storage_properties['swift_tenant_name']

    return storage_dict


def _validate_setup_props(properties):
    root_anchor = os.path.join(properties.get('root', os.path.expanduser("~")), "AeroFS")
    rtroot = properties.get('rtroot', os.path.join(os.path.dirname(root_anchor), ".aerofs-storage-agent"))
    device_name = properties['device_name']
    contact_email = properties['contact_email']
    token = properties['token']
    port = properties.get('force_port', None)
    storage_dict = get_storage_dict(properties)

    return root_anchor, rtroot, device_name, contact_email, storage_dict, token, port


# taken from Drew's secutil.py
alpha = "abcdefghijklmnop"


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


# end of secutil stuff


def _create_rtroot_with_perms(rtroot):
    if not os.path.exists(rtroot):
        os.makedirs(rtroot)

    # check perm
    if not os.access(rtroot, os.W_OK | os.R_OK):
        raise Exception("User doesn't have permissions to access AeroFS rtroot")


def _create_sa_conf_file(root_anchor, did, sa_user_id, contact_email, storage_dict, port, sa_conf_dir):
    cfg_data = {
        'root': os.path.abspath(root_anchor),
        'device_id': did.hex,
        'user_id': sa_user_id,
        'contact_email': contact_email,
        'rest_service': str(True),
    }
    if port is not None:
        cfg_data['force_port'] = port
    cfg_data.update(storage_dict)

    with open(os.path.join(sa_conf_dir, "storage_agent.conf"), 'w') as f:
        for key, val in cfg_data.items():
            f.write(key + " " + val)
            f.write("\n")


def _sanitize_required_folder_locations(folder):
    if not folder:
        raise Exception("No location provided for {}".format(folder))
    if os.path.isfile(folder):
        raise Exception("A file {} already exists.".format(folder))


def _create_root_anchor(root_anchor):
    if os.path.exists(root_anchor):
        shutil.rmtree(root_anchor)
    os.makedirs(root_anchor)
    # check perm
    if not os.access(root_anchor, os.W_OK | os.R_OK):
        raise Exception("User doesn't have permissions to access AeroFS directory")


def _create_csr(sa_user_id, did, key):
    csr_req = crypto.X509Req()
    cname = unicode(alphabet_encode(magichash(unicode(sa_user_id), did.bytes)))
    subj = csr_req.get_subject()
    subj.CN = cname
    subj.O = "aerofs.com"
    subj.ST = "CA"
    subj.C = "US"
    subj.L = "SF"
    subj.emailAddress = "support@aerofs.com"
    csr_req.set_pubkey(key)
    csr_req.sign(key, "sha1")
    return csr_req


def _write_cert_to_file(rtroot, cert):
    # Write cert
    cert_file = os.path.join(rtroot, "cert")
    with open(os.path.join(cert_file), 'w') as f:
        f.write(str(cert))
    os.chown(cert_file, owner_uid, pwd.getpwuid(owner_uid).pw_gid)


def _write_key_to_file(rtroot, key):
    # Write temp key
    key_file = os.path.join(rtroot, "key.pem")
    temp_file = os.path.join(rtroot, "key_temp")
    with open(temp_file, 'w') as f:
        f.write(crypto.dump_privatekey(crypto.FILETYPE_PEM, key))
    # FIXME (RD) remove when syncdet runs SA in container
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
    pb_interface = RegisterDeviceCall.Interface()
    # hardcoded because we don't validate the interfaces for Storage Agents
    pb_interface.name = "eth0"
    pb_interface.mac = "00:00:00:00:00:00"
    pb_interfaces.append(pb_interface)
    return pb_interfaces


def _install_sa_user_impl(sp, token, device_name, rtroot):
    # TODO (RD) generalize setup to an organization
    sa_user_id = ":2"

    # Generate new RSA keypair
    key = crypto.PKey()
    key.generate_key(crypto.TYPE_RSA, 2048)

    # Generate did
    did = uuid.uuid4()

    # Get CSR
    csr_req = _create_csr(sa_user_id, did, key)

    full_os_name = ' '.join(platform.linux_distribution())

    pb_network_interfaces = _get_network_interfaces()

    # DER enconding
    der_encoded_req = crypto.dump_certificate_request(crypto.FILETYPE_ASN1, csr_req)

    cert = sp.register_storage_agent(did.bytes, der_encoded_req, platform.system(), full_os_name, device_name,
                                     pb_network_interfaces, token).cert

    _write_key_to_file(rtroot, key)
    _write_cert_to_file(rtroot, cert)

    return did, sa_user_id


def _install_user(sp, token, device, rtroot, root_anchor):
    assert sp is not None

    _create_root_anchor(root_anchor)
    return _install_sa_user_impl(sp, token, device, rtroot)


def _write_site_config(config, dest):
    with open(dest, 'w') as f:
        for k, v in config.iteritems():
            if k.startswith("config.loader"):
                f.write("{}={}\n".format(k, v))


def setup_storage_agent(bundle):
    cfg = bundle_reader(bundle, os.path.dirname(bundle))
    setup_properties, site_config = cfg.read_bundle()
    root_anchor, rtroot, device, contact_email, storage_dict, token, port = _validate_setup_props(setup_properties)
    print ("=== Validated input ===")

    _sanitize_required_folder_locations(rtroot)
    _sanitize_required_folder_locations(root_anchor)
    _create_rtroot_with_perms(rtroot)

    ca_cert_file = os.path.join(os.path.dirname(bundle), "cacert.pem")
    with open(ca_cert_file, 'w') as f:
        f.write(site_config['config.loader.base_ca_certificate'].replace('\\n', '\n'))

    sp = SPServiceRpcStub(SyncConnectionService(site_config['base.sp.url'], SP_PROTO_VERSION, cacert=ca_cert_file))
    did, sa_user_id = _install_user(sp, token, device, rtroot, root_anchor)
    print ("=== Installed storage agent ===")

    _create_sa_conf_file(root_anchor, did, sa_user_id, contact_email, storage_dict, port, rtroot)
    print ("=== Finished configuring storage agent ===")

    # N.B. this location of site config is expected by the SA startup script
    _write_site_config(site_config, os.path.join(rtroot, SITE_CONFIG_FILENAME))
    print ("***Successfully completed installing storage agent. Ready to run***")


def main(argv):
    if len(argv) != 2:
        print "usage: {} path_to_config_bundle".format(argv[0])
        sys.exit(1)
    setup_storage_agent(argv[1])


if __name__ == "__main__":
    main(sys.argv)
