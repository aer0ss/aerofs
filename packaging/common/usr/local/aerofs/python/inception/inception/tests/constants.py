"""
Constants used for tests.
"""

# TestInceptionInfrastructure system constants.
TII_SYSTEM_CONSTANTS = {}

# The name is the file name, the value is the file content.
TII_SYSTEM_CONSTANTS['admin.addr'] = 'localhost'
TII_SYSTEM_CONSTANTS['vmhost.addr'] = 'localhost'
TII_SYSTEM_CONSTANTS['vmhost.id'] = 'd8e8fca2dc0f896fd7cb4cb0031ba249'
TII_SYSTEM_CONSTANTS['service1.name'] = 'sp-daemon'
TII_SYSTEM_CONSTANTS['service2.name'] = 'admin-panel'
TII_SYSTEM_CONSTANTS['service3.name'] = 'xmpp'
TII_SYSTEM_CONSTANTS['interfaces'] = 'auto lo\n' + \
'iface lo inet loopback\n' + \
'\n' + \
'auto eth0\n' + \
'iface eth0 inet dhcp\n' + \
'    dns-nameservers something.company.com'

# Generated using the script in the tools directory.
TII_SYSTEM_CONSTANTS['cert.pem'] = '-----BEGIN CERTIFICATE-----\n' + \
'MIIC/zCCAeegAwIBAgIJAKprPvJ+c81JMA0GCSqGSIb3DQEBBQUAMBYxFDASBgNV\n' + \
'BAMMC21waWxsYXIuY29tMB4XDTEyMDUyNTExNDQ1MFoXDTIyMDUyMzExNDQ1MFow\n' + \
'FjEUMBIGA1UEAwwLbXBpbGxhci5jb20wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAw\n' + \
'ggEKAoIBAQCkaGukiHnVSfP4Hx6Jj3G0Ghqz5ZzBylth/uU6byt4qzAk3magofIZ\n' + \
'eV0kH++6dGM/pyWRiYwthTPD33NlIusOiU3RqvQSNf7vhCgz6NOdoZizrnkmcZYa\n' + \
'qSTHmWDR6MCcHyi7kKxDwZidKE5/OhoU501f/aXppmDHxMzZ6gE2LdUBg08V6xb3\n' + \
'bgGFxajxZVWhTTOCqTM8s8LxMjGe+fmtwvzyXRm5ddnTPVAWDHrzU7cnafBc1k1R\n' + \
'6ohMpQ+Uddba3wKZePGKwH1BD5DH2l1N+uGH7bzOagdgrzG043r3vCv1YgEsxwdO\n' + \
'xr91u1OyKKjRiU9ivBZcDBxl0SKZK0nbAgMBAAGjUDBOMB0GA1UdDgQWBBQegSVe\n' + \
'YHlX8W2miA8u2b/H6fwl3zAfBgNVHSMEGDAWgBQegSVeYHlX8W2miA8u2b/H6fwl\n' + \
'3zAMBgNVHRMEBTADAQH/MA0GCSqGSIb3DQEBBQUAA4IBAQBYN9ZvJPzzSLUT0e/s\n' + \
'I9lRjtmW/E/aI7jgHHoiH/J5dY0JG7cFz1fyyikC0PpMgbpjG3A+NrjfndjSa5nS\n' + \
'5xjTtBUqiViI4nE42SrI9YXHelsBlrdo3wlAM0O/XhzmjyWFrr4WOJmZKGAlJ3Re\n' + \
'HiCDRRVOt/wsdfCDJWYH/Lyu+ZNbq3r1J/V7WZFWvoMiIToULjCFPVWZWJeWt+cg\n' + \
'bsasaxbJ5L9349I0BgChh6FhmtDVFpM38oxk3Nqpe8kk4ELZahfI4H5KmCHCRncg\n' + \
'cpE19qPwDg/Nf6yHtSfysbpL5A109dlQN2UhTXd4JiQXyQDtLhrBhS48SHiwve7t\n' + \
'NsGk\n' + \
'-----END CERTIFICATE-----\n' + \
'-----BEGIN RSA PRIVATE KEY-----\n' + \
'MIIEowIBAAKCAQEApGhrpIh51Unz+B8eiY9xtBoas+WcwcpbYf7lOm8reKswJN5m\n' + \
'oKHyGXldJB/vunRjP6clkYmMLYUzw99zZSLrDolN0ar0EjX+74QoM+jTnaGYs655\n' + \
'JnGWGqkkx5lg0ejAnB8ou5CsQ8GYnShOfzoaFOdNX/2l6aZgx8TM2eoBNi3VAYNP\n' + \
'FesW924BhcWo8WVVoU0zgqkzPLPC8TIxnvn5rcL88l0ZuXXZ0z1QFgx681O3J2nw\n' + \
'XNZNUeqITKUPlHXW2t8CmXjxisB9QQ+Qx9pdTfrhh+28zmoHYK8xtON697wr9WIB\n' + \
'LMcHTsa/dbtTsiio0YlPYrwWXAwcZdEimStJ2wIDAQABAoIBAHgnOEQO2btfSMXV\n' + \
'OGQgSWDukwVWkbGvSgncV3rVNFgEBDNttmM+98hWQhPcoz8JQF+MsJAkjiXWa3aD\n' + \
'H7qSQmdlQVIyNh35CE/Tre4CAmX5a7glkrTd3m2toAuftHq0N8/hlcs+eCcsnXR9\n' + \
'uKCSvez4/jthDJgt2B0nXu8Dbc9Bk6gIxjqJpDatjfaTodT9VnqbifIiupnv1vv/\n' + \
'NgN0vcWm7bA4DbU6e5TPQfGZjVPG1+kSsLGzqclG0y4UFclLgraaxnLVnpwoGEc3\n' + \
'Rw0kE/+Bz6133UYkb5V6XM0K8ZhmoA8gzaBz1QH2aCTZ0vfQhCYiRtBV8dPG7Z0l\n' + \
'9xN95MECgYEA0sm1G2T+0ny8s4ypFqTovGlMCWBS2QM3BZN4GdFFwf59kILIwyPw\n' + \
'SuL9vLIsj2piPQk85n5Ch703U69Dmuz1+abOd4XP+vF+rzWOpTNAtEGPsEqC2WPw\n' + \
'QVDST0UjQLuL3iDX//4bwodzl2TCMH495wNEh5oOU78qcrFxG9VpTicCgYEAx6wA\n' + \
'aR9e9GOtuxKhYTUv9I2OHiGV3hHANX/Q1Idkl9Zp4SFtcDYf6kaxR4fRC6lVIRfr\n' + \
'TtwH+RRVUzRIP/IxbA0J7mthYGAuJ5YspqD71GMldM2wnTc9qo64JF/jd+s+f2Z4\n' + \
'1S0fM+aFWD0XHN2cL+Abk6V2YBYOrlfz/ms4Ky0CgYA2arplmxoqDufMVpjkbqhi\n' + \
'07A8j3Bz89+Fgf+0cpFWtrOF1i2sKyACxT0KNNlKIQXBihv9yUS1tESPRyTl3xzV\n' + \
'WZndw17hAu1YoDP2NBgVMQO+WE4VfzZsNkhFDK1ALeq66rG4tcpG2x8dS3bSfBTR\n' + \
'CcfsVfoy5pw3xdVUgTDRawKBgQCyTWM0fGITxKtQIYvgvVksfffJ6l3lJ17Hp3K5\n' + \
'RopKmJqGAo6ypHKNh0Epyhuf2T9+xNhI3G12PHURTt+x0rnVbjNPJ66WHG06xnzq\n' + \
'81jOSRcanc84JSs5EidXvBTpcjWmGMCQRLHXa+Ppbuwjx7WAfxTBHQF/PlMv1rS4\n' + \
'ndBTpQKBgEUnjPwfBj4u2lWjv761QYZ3F9A85UsUNbbLX/8CcV5G2ZrkZfxbajCQ\n' + \
'lf+pEconI95PxdUVkfa+Qxs6J8eui8VAjVkhV+8jXtyZHsC4NavgMdlDTBM9SNQj\n' + \
'FmmJZHpAR9FoSuNOHLWw6GGtedDI67W+MzmGdmmRWD1byRUOWG22\n' + \
'-----END RSA PRIVATE KEY-----'
