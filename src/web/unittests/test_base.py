import unittest, os
from pyramid import testing
from mock import Mock, create_autospec
from web import util
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

        self.sp_rpc_stub = create_autospec(SPServiceRpcStub)

        util.get_rpc_stub = Mock(return_value=self.sp_rpc_stub)

    def create_request(self, parameters):
        """
        @param parameters a dictionary of HTTP request parameters
        """
        request = testing.DummyRequest()
        request.params = parameters
        request.translate = Mock()
        return request