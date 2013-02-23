import unittest, os
from pyramid import testing
from mock import Mock
from aerofs_web import helper_functions
from aerofs_sp.gen.sp_pb2 import SPServiceRpcStub

class TestBase(unittest.TestCase):
    def setup_common(self):
        """
        Derived test classes should call this method at the beginning of
        setUp()
        """
        os.environ['STRIPE_PUBLISHABLE_KEY'] = ''
        os.environ['STRIPE_SECRET_KEY'] = ''

        self.config = testing.setUp()
        self.sp_rpc_stub = SPServiceRpcStub(None)

        helper_functions.get_rpc_stub = Mock(return_value=self.sp_rpc_stub)
