#!/usr/bin/env python
import unittest
import datetime
import os

from tests.util import LicenseTestCase
from aerofs_licensing import license_file
from io import BytesIO

def to_bytesio(hex_string):
    return BytesIO("".join(hex_string.split()).decode("hex"))

def make_passphrase_cb(password):
    def inner_passphrase_cb(uid_hint, passphrase_info, prev_was_bad, fd):
        if prev_was_bad:
            raise IOError("password was wrong")
        data_to_write = password + '\n'
        os.write(fd, data_to_write)
    return inner_passphrase_cb

class LicenseFileTestCase(LicenseTestCase):
    def test_valid_expired_license_is_not_currently_valid(self):
        # license_type=normal
        # license_seats=3,
        # license_valid_until=2013-11-05,
        # customer_id=1
        buf = to_bytesio("""
            a3019bc0cbccc0c4e83fa75ec8e544453fe3e9b749ccba6a1641d542223999c9
            a979c5a9ba997969f90c3402064060666000a60d306903432343064323236333
            334343032313060343632373430605035a390819941697241629283014e5e797
            e05347481edd73430440e33fbea4b220d5362fbf283731870b26569c9a58526c
            6bcc950c0ca3fcdcd4a2f8cc145b43b86c59624e664a7c695e49668ead1130ca
            740d0d750d4cb906da43a360148c8251300a46c1281805a360148c8251300a46
            c1281805a360148c8251300a46c1281805a360148c825130b2c0eb8176c02818
            05a360148c8251300ae80f38e1ac4e261916064626063656a6a06a2111062e4e
            01ff39f5422e272afaa3eaf9ff57b80b7f107378b7da986bb259cab3cfe539ca
            cb97da4f5d7ccae15487016bac6bf2e4bb11ff965a5cd54b6dfff6c3d94579de
            fd809eee0b473d9ecd8afea976eb92a1f4a79f0ba2d5b77398796fcabdd1a6fb
            b4232783b3e99a99f63df6ba22c160d74fad6f16fe4dff60e6c9ac55f8e967dd
            857bef7bcedf7deba4f6f3fb14cd43cb9b159b6d76cfd5dbe6f172af55ad468f
            46baeaaef7a7d8bdd4bf9646b24d912a3f76acb461d2994f0f3b5f2c48addcb0
            a5ebe4d4e58266b3cff9dd3bd6f26897fafe948d655a2cc9ba16ee9ff70989f0
            f30be84cd79e756cf972837396cffe479de94bd97935f4f73d0905bde34e0f37
            87b73a4d5db3e0d46cf5a2af972f294a4d2c0e5012e459717d2fc335e1355f14
            5727bad8053adf79d0b078ab32cb4c636ef63785ddbcfb9684b165fb30ef9f17
            b6d13729c4dba429303d2a48a780cf417bbb83859c4e75fee3de3ee126910542
            c64faf65d72b0bbd0eec35deb157e4e2826fffe74d942ad13e187ceacbf6cd6a
            a7afadb87230d8bb24ac5b741aa3f39e43aacf7c7f71dc38979110e915d63a9b
            498eff4250ef91ed3676fb175a57f839d55495e9ede44a581b9db0651ac3a98b
            ebdfef603a9bf3f38fedc3998f857918be16ce0b5a79cc6ed194a3a78fbe115a
            eb6e9f6af37c5df0bc851a27dfbec9f99cf5972d23d357fcd8fdf7c6768fb37d
            588db6bb793ae5f5f6b77b1bdf7ed4fae90e834e68ff8f8f13ee1ffd72e35840
            d49ea65987a7477fb90900
            """)
        license = license_file.verify_and_load(buf, gpg_homedir=self.gpg_dir())
        self.assertFalse(license.is_currently_valid())

    def test_valid_license_is_currently_valid(self):
        # license_type=normal
        # license_seats=0
        # customer_id=1
        # license_valid_until=2053-10-27
        buf = to_bytesio("""
            a3019bc0cbccc0c4e83fa75ec8e544453fe3e9b749ccba6a1641d5c28a3999c9
            a979c5a9ba997969f90c3402064060666000a60d306903432343064323236333
            3343431313a0b8a1b191b931838201ad1c840c4a8b4b128b1414188af2f34bf0
            a923248feeb92102a0f11f5f5259906a9b975f949b98c305132b4e4d2c29b635
            e04a0686517e6e6a517c668aad215cb62c31273325be34af2433c7d6c8c0d458
            d7d040d7c89c6ba03d340a46c1281805a360148c8251300a46c1281805a36014
            8c8251300a46c1281805a360148c8251300a46c1281805230bbc1e68078c8251
            300a46c1281805a380fe8013ceea649261616064626063650aaa165664e0e214
            f09f532fe472a2a25fe7950043ef2737be67b1d2e7d3ed8ca3f6ff99e8d99b9b
            dab4d957ac5acbd97261ce9ddfb718cad9e3ea16b6fd50f975995d7e7d8a99de
            5df6c83507ac0a2fe709891c5c9f1fb0e8302743aa845773ccf708f5c0cf87a2
            ad4fcfcf7dbd3346bec9302fea6bb4a3cff2ac3073ff33a1bac1db85c53fb14c
            2fcee9b93d315dd840ac4b76fb19850f8e6fab7e7ce758ffe0fc5bf3b636b7d5
            0c4d256fffeddf2b7279f68c2d052c1b83bd9732d7e5e8981ccbd43fb0f255e0
            eefba76f68c72df149f1738f63db3ac5b2b1676dccfccff6bda649b3723d1ef5
            ff887bbdeee299a88279f6176e88c5ba9e537a58a87dcbebd0c634c1ab5ad191
            99bb9f5d62dbe972fe2aebac400b8fb945bbfecf7dc5b37defe78a4f82fa3682
            1393f59f323c383fdd3a736df5c67ad3f82acb235a1f8cb63cf8a1aba89bb17b
            de39d1c009e9c72747ac73d832f1fcf9beb92726b43fb23193bbe2b174fef470
            d30a9b65496fa554e615da66f69aac5cc1b462666caca0d739d1b98b799d9c65
            5ec465dc9efde8cd75f57dd506b784c5e76a3e58d06b76564af27eeed6e59ff9
            db7833856f2bbc5933a76e7decec451dabf6bfd81268f831e0d0f3889e93abd3
            6bda6604553515c6ce997440c3bbb2283c648ef91e667e26de495b2bed444eed
            ff167eebbe46996fd9f225abdf2786bab7b35c59d07636b0e09e4d9b4bf28af5
            326b0cb45416bdfe6117abba22c8d437eb60e95bd77f1fb4dfb9261d0ffd54c2
            d5baeac2419d9a2d00
            """)
        license = license_file.verify_and_load(buf, gpg_homedir=self.gpg_dir())
        self.assertTrue(license.is_currently_valid())

    def test_nonsense_file_fails_to_load(self):
        buf = to_bytesio("")
        self.assertRaises(Exception, license_file.verify_and_load, buf, gpg_homedir=self.gpg_dir())

    def test_build_and_verify_should_roundtrip(self):
        tomorrow = (datetime.datetime.today() + datetime.timedelta(days=1)).date().isoformat()
        d = {
                "license_type":"normal",
                "customer_id":"1",
                "license_valid_until":tomorrow,
                "license_seats":"3",
            }
        f = BytesIO()
        license_file.generate(d, f, gpg_homedir=self.gpg_dir(), password_cb=make_passphrase_cb("temp123"))
        self.assertTrue(len(f.getvalue()) > 0)
        # rewind f
        f.seek(0)
        # load from license file
        license = license_file.verify_and_load(f, gpg_homedir=self.gpg_dir())
        self.assertTrue(set(license.keys()) == set(d.keys()))
        for key in d:
            self.assertTrue(key in license and license[key] == d[key])

def test_suite():
    loader = unittest.TestLoader()
    return loader.loadTestsFromName(__name__)
