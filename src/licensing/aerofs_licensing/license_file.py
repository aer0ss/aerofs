#!/usr/bin/env python

from .crypto import verify, sign
from io import BytesIO
import tarfile
import datetime
import time

class LicenseInfo(dict):
    def is_currently_valid(self):
        if self.is_perpetual():
            return True
        expiry_string = self.get("license_valid_until", None)
        if expiry_string:
            now = datetime.datetime.today()
            expiry_date = datetime.datetime.strptime(expiry_string, "%Y-%m-%d")
            if expiry_date < now:
                return False
        return True

    def allows_audit(self):
        return self.get("license_allow_auditing", "") == "true"

    def is_trial(self):
        return self.get("license_is_trial", "") != "false"

    def is_perpetual(self):
        return self.get("license_perpetual") == "true"

    def is_unlimited_seating(self):
        return self.get("license_unlimited_seating") == "true"

    def issue_date(self):
        issue_datestring = self.get("license_issue_date", "1970-01-01T00:00:00Z")
        return datetime.datetime.strptime(issue_datestring, "%Y-%m-%dT%H:%M:%SZ")

    def seats(self):
        """Returns the seat limit for this license file.  If unlimited, returns -1."""
        return int(self.get("license_seats", "-1"))


def _verify_and_open_tarfile(file_handle, gpg_homedir=None):
    """
    Verifies that file_handle is signed, then opens the signed data as a
    tarfile and returns the TarFile object.
    """
    out_buf = BytesIO()
    # verify file, signed contents will be placed in out_buf
    verify(file_handle, out_buf, gpg_homedir)
    # seek back to beginning of "file" so the next reader will be able to read the data
    out_buf.seek(0)
    # the signed contents are a tarball containing at least a file named 'license-info'
    tarball = tarfile.open(fileobj=out_buf)
    return tarball

def verify_and_load(file_handle, gpg_homedir=None):
    """
    Returns the contents of the license file's license-info file as a dictionary.

    Verifies and reads a license file from `file_handle`.  This dictionary is
    guaranteed to have at least the keys `license_type` and `customer_id`.
    """
    tarball = _verify_and_open_tarfile(file_handle, gpg_homedir)
    license_info_file = tarball.extractfile("license-info")
    if not license_info_file:
        raise ValueError("No file license-info found in signed tarball")
    license_info = LicenseInfo()
    for line in license_info_file:
        # Ignore comments.
        if line.startswith("#"):
            continue
        # Ignore lines that don't have an = in them.
        if not "=" in line:
            continue
        key, value = line.rstrip("\n").split("=", 1)
        license_info[key.decode("utf-8")] = value.decode("utf-8")
    if "license_type" not in license_info:
        raise ValueError("No license_type found in license-info")
    if "customer_id" not in license_info:
        raise ValueError("No customer_id found in license-info")
    return license_info

def verify_and_extract(file_handle, output_dir, gpg_homedir=None):
    """
    Extracts the contents of the license file opened from `input_fileobj` to
    the filesystem at the base path `output_dir`.

    Returns a list of the relative paths of files so extracted.
    """
    tarball = _verify_and_open_tarfile(file_handle, gpg_homedir)
    tarball.extractall(path=output_dir)
    return tarball.getnames()

def _add_bytesio_to_tarball(tarball, path, fileobj):
    fileinfo = tarfile.TarInfo(name=path)
    fileinfo.uid = fileinfo.gid = 0
    fileinfo.uname = fileinfo.gname = "root"
    fileinfo.size = len(fileobj.getvalue()) # length in bytes
    fileinfo.mode = 0600 # permission bits in octal
    fileinfo.mtime = int(time.time()) # time in integer seconds since the epoch
    # rewind fileobj
    fileobj.seek(0)
    # Add to tarball
    tarball.addfile(fileinfo, fileobj)

def generate(license_info, output_file_handle, gpg_homedir=None, password_cb=None):
    """
    Generates and signs a license file.

    This function generates a license file containing all of the keys in the
    dictionary `license_info`, which should have only string keys and values.
    The signed license file is written to `output_file_handle`.

    Future work: include a GPG keypair in the license file.  Allow specifying
    an existing one in `keypair`.
    """
    # license_info_flo is a file-like object that will contain the license-info
    # file to put in the signed tarball
    license_info_flo = BytesIO()
    if "license_type" not in license_info:
        raise ValueError("No license_type found in license_info dictionary")
    if "customer_id" not in license_info:
        raise ValueError("No customer_id found in license_info dictionary")
    if "license_issue_date" not in license_info:
        raise ValueError("No license_issue_date found in license_info dictionary")

    # verify license_info contains at least license_type and customer_id
    for key, value in license_info.iteritems():
        print >>license_info_flo, "{}={}".format(key.encode("utf-8"), value.encode("utf-8"))
    tarfile_buf = BytesIO()
    with tarfile.open(mode="w", fileobj=tarfile_buf) as tarball:
        _add_bytesio_to_tarball(tarball, "license-info", license_info_flo)

    # tarfile_buf is now complete.  Let's sign it!
    tarfile_buf.seek(0)
    sigs = sign(tarfile_buf, output_file_handle, gpg_homedir, password_cb)
    return sigs
