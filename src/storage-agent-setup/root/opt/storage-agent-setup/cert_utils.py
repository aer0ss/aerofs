import uuid
import hashlib

from OpenSSL import crypto

def new_ca_cert_name():
    return "AeroFS-Storage-" + uuid.uuid4()

def new_csr(cname, pkey):
    csr = crypto.X509Req()
    subj = csr.get_subject()
    subj.CN = cname
    subj.O = "aerofs.com"
    subj.ST = "CA"
    subj.C = "US"
    subj.L = "SF"
    subj.emailAddress = "support@aerofs.com"
    csr.set_pubkey(pkey)
    csr.sign(pkey, "sha1")

    return csr

def self_sign_csr(pkey, csr, serial=0, days=365):
    cert = crypto.X509()
    cert.set_subject(csr.get_subject())
    cert.set_serial_number(serial)
    cert.gmtime_adj_notBefore(0)
    # need to get the seconds for which the cert is valid
    cert.gmtime_adj_notAfter(days * 24 * 60 * 60)
    cert.set_issuer(csr.get_subject())
    cert.set_pubkey(csr.get_pubkey())
    cert.sign(pkey, "sha1")
    return cert

def write_key_to_file(key, file):
    with open(file, 'w') as f:
        f.write(crypto.dump_privatekey(crypto.FILETYPE_PEM, key))

def write_cert_to_file(cert, file):
    with open(file, 'w') as f:
        f.write(crypto.dump_certificate(crypto.FILETYPE_PEM, cert))
