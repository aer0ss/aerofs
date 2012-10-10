"""
This is an example of how to attach to the inception protobuf communication
interface via the VM hosts manager server application. This example can be used
to assist in the development of the admin panel web application.
"""

import sys
import socket
import inception.app.user

def main():
    if len(sys.argv) != 2:
        print 'Usage: ' + sys.argv[0] + ' <admin-address>'
        sys.exit(1)

    try:
        # Create admin protobuf client generator. The Admin panel will just
        # specify 'localhost' here.
        agenerator = inception.app.user.create_admin_generator(sys.argv[1])

    except socket.error, e:
        # The vmhosts manager is down, you provided the wrong address, etc.
        print 'Socket error: ' + str(e)
        sys.exit(2)

    try:
        # (1) Use aclient() to access the admin.proto interface.
        areply = agenerator.aclient().get_vm_host_ids_list()
        print '==> VM host IDs:\n' + str(areply)

        # (2) Use vclient(vm_host_id) to access the vmhost.proto interface.
        vreply = agenerator.vclient(areply.vmHostIds[0]).get_services_list()
        print '==> Services:\n' + str(vreply)

        # (3) Use vgenerator(vm_host_id).kclient(service_name) to access the
        # kvm.proto interface
        kreply = agenerator.vgenerator(areply.vmHostIds[0]).kclient(vreply.serviceNames[0]).get_status()
        print '==> Status:\n' + str(kreply)

    except inception.common.impl.NetworkImplException, e:
        # There was an error in your request, one of the managers is down, etc.
        print 'Inception error: ' + str(e)
        sys.exit(3)

    # Cleanly dispose of the connection.
    agenerator.disconnect()

if __name__ == '__main__':
    main()

# Example output:
#
# ==> VM host IDs:
# vmHostIds: "32be65e4cd84c039efa88aa26cdccd73"
#
# ==> Services:
# serviceNames: "sp-daemon"
# serviceNames: "admin-panel"
# serviceNames: "xmpp"
# statuses: CONNECTED
# statuses: CONNECTED
# statuses: DISABLED
#
# ==> Status:
# hostname: "sp-daemon"
# status: GOOD
# networkType: DHCP
