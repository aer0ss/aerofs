"""
Simple wrapper for the admin proto interface client generator.
"""

import socket
import inception.common.impl
import inception.admin.user
import inception.vmhost.user
import inception.app.constants

def create_admin_generator(admin_address):
    aimpl = inception.common.impl.NetworkConnectImpl(
            admin_address,
            inception.app.constants.VMHOSTS_CLT_PORT,
            inception.app.constants.CERT_KEY_FILE)

    agenerator = inception.admin.user.ProtoClientGenerator(aimpl)
    return agenerator
