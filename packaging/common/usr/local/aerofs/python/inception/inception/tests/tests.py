"""
Inception system tests. Creates instances of the inception server classes,
injects requests and compares responses to what is expected.
"""

import os
import time
import random
import unittest
import tempfile
import logging
import inception.app.user
import inception.kvm.config
import inception.kvm.manager
import inception.gen.kvm_pb2
import inception.admin.manager
import inception.app.constants
import inception.vmhost.manager
import inception.tests.constants

class TestInceptionInfra(unittest.TestCase):

    # Create a logger given a name and a log file.
    @staticmethod
    def create_logger(name, log_file):
        fmt = '%(asctime)-6s %(name)s:%(levelname)s %(message)s'
        logger = logging.getLogger(name)
        hdlr = logging.FileHandler(log_file)
        formatter = logging.Formatter(fmt)
        hdlr.setFormatter(formatter)
        logger.addHandler(hdlr)
        logger.setLevel(logging.DEBUG)
        return logger

    def setUp(self):
        # Create all test files that we require.
        self._temp_dir = tempfile.mkdtemp()

        for key in inception.tests.constants.TII_SYSTEM_CONSTANTS:
            fh = open(self._temp_dir + '/' + key, 'w')
            fh.write(inception.tests.constants.TII_SYSTEM_CONSTANTS[key])
            fh.close()

        # Create autostart and images directory.
        os.mkdir(self._temp_dir + '/autostart')
        os.mkdir(self._temp_dir + '/images')

        # Create loggers.
        self._vmhosts_manager_logger = TestInceptionInfra.create_logger(
                'VmHostsManager',
                self._temp_dir + '/vmhosts_manager.log')
        self._kvms_manager_logger = TestInceptionInfra.create_logger(
                'KvmsManager',
                self._temp_dir + '/kvms_manager.log')
        self._services1_manager_logger = TestInceptionInfra.create_logger(
                'Services1Manager',
                self._temp_dir + '/services1_manager.log')
        self._services2_manager_logger = TestInceptionInfra.create_logger(
                'Services2Manager',
                self._temp_dir + '/services2_manager.log')
        self._services3_manager_logger = TestInceptionInfra.create_logger(
                'Services3Manager',
                self._temp_dir + '/services3_manager.log')

        # Create 1 vmhosts_manager
        self._vmhosts_manager = inception.admin.manager.VmHostsManager(
                self._vmhosts_manager_logger,
                inception.app.constants.VMHOSTS_CLT_PORT,
                inception.app.constants.VMHOSTS_SRV_PORT,
                self._temp_dir + '/cert.pem')

        # Create 1 kvms_manager
        self._kvms_manager = inception.vmhost.manager.KvmsManager(
                self._kvms_manager_logger,
                inception.app.constants.KVMS_CLT_PORT,
                inception.app.constants.KVMS_SRV_PORT,
                inception.app.constants.VMHOSTS_SRV_PORT,
                self._temp_dir + '/admin.addr',
                self._temp_dir + '/vmhost.id',
                self._temp_dir + '/autostart',
                self._temp_dir + '/images',
                self._temp_dir + '/cert.pem')

        # Create 3 services_manager's.
        self._services1_manager = inception.kvm.manager.ServicesManager(
                self._services1_manager_logger,
                inception.app.constants.KVMS_SRV_PORT,
                self._temp_dir + '/vmhost.addr',
                self._temp_dir + '/service1.name',
                self._temp_dir + '/cert.pem')
        self._services2_manager = inception.kvm.manager.ServicesManager(
                self._services2_manager_logger,
                inception.app.constants.KVMS_SRV_PORT,
                self._temp_dir + '/vmhost.addr',
                self._temp_dir + '/service2.name',
                self._temp_dir + '/cert.pem')
        self._services3_manager = inception.kvm.manager.ServicesManager(
                self._services3_manager_logger,
                inception.app.constants.KVMS_SRV_PORT,
                self._temp_dir + '/vmhost.addr',
                self._temp_dir + '/service3.name',
                self._temp_dir + '/cert.pem')

        # Set up the kvm system config files.
        inception.kvm.config.INTERFACE_FILE = self._temp_dir + '/interfaces'

        # Server startup.
        self._vmhosts_manager.start()
        self._kvms_manager.start()
        self._services1_manager.start()
        self._services2_manager.start()
        self._services3_manager.start()

    def tearDown(self):
        self._services3_manager.shutdown()
        self._services2_manager.shutdown()
        self._services1_manager.shutdown()
        self._kvms_manager.shutdown()
        self._vmhosts_manager.shutdown()

    def test_system(self):
        # Create admin client generator.
        aimpl = inception.common.impl.NetworkConnectImpl(
                'localhost',
                inception.app.constants.VMHOSTS_CLT_PORT,
                self._temp_dir + '/cert.pem')
        agenerator = inception.admin.user.ProtoClientGenerator(aimpl)

        # It will take a little time for the servers to bind to their ports
        # and connect up, unfortunately.
        for i in range(100):
            ids = agenerator.aclient().get_vm_host_ids_list()
            if len(ids.vmHostIds) == 0:
                time.sleep(0.01)
                continue

            services = agenerator.vclient(ids.vmHostIds[0]).get_services_list()
            if len(services.serviceNames) != 3:
                time.sleep(0.01)
                continue

        # Assertions for the stuff we had to wait for.
        self.assertEqual(len(ids.vmHostIds), 1)
        self.assertEqual(ids.vmHostIds[0], inception.tests.constants.TII_SYSTEM_CONSTANTS['vmhost.id'])

        self.assertEqual(len(services.serviceNames), 3)
        self.assertTrue('sp-daemon' in services.serviceNames)

        # Everything is up now. Do some more testing.
        status = agenerator.vgenerator(ids.vmHostIds[0]).kclient('sp-daemon').get_status()
        self.assertEqual(status.dns, "something.company.com")

        result = agenerator.vgenerator(ids.vmHostIds[0]).kclient('sp-daemon').configure_network(
                inception.gen.kvm_pb2.ConfigureNetworkCall.STATIC,
                'test-address',
                'test-netmask',
                'test-network',
                'test-broadcast',
                'test-gateway',
                'test-dns.something.com')
        self.assertEqual(
                inception.gen.kvm_pb2.ResultReply.SUCCESS,
                result.result)

        status = agenerator.vgenerator(ids.vmHostIds[0]).kclient('sp-daemon').get_status()
        self.assertEqual(
                status.status,
                inception.gen.kvm_pb2.GetStatusReply.GOOD)
        self.assertEqual(status.address, 'test-address')
        self.assertEqual(status.netmask, 'test-netmask')
        self.assertEqual(status.network, 'test-network')
        self.assertEqual(status.broadcast, 'test-broadcast')
        self.assertEqual(status.gateway, 'test-gateway')
        self.assertEqual(status.dns, 'test-dns.something.com')

if __name__ == '__main__':
    unittest.main()
