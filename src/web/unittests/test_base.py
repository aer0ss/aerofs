import unittest, os
import binascii
from pyramid import testing
from mock import Mock
from web import util
from aerofs_sp.gen.sp_pb2 import SPServiceRpcStub
import web.views as views


class TestBase(unittest.TestCase):
    def setup_common(self):
        """
        Derived test classes should call this method at the beginning of
        setUp()
        """
        self.config = testing.setUp()

        ### Making the test config more like our real one
        # Static views
        self.config.add_static_view('static', 'web:static')

        # Import routes from views
        for view in views.__all__:
            self.config.include('{}.{}'.format(views.__name__, view))

        self.config.scan()
        self.config.commit()
        ###

        # Use a real stub to verify that the callers (i.e. systems under test)
        # provide correct parameters (since the stub serializes all the
        # parameters according to proto file definition.
        self.sp_rpc_stub = SPServiceRpcStub(NullServiceConnection())

        util.get_rpc_stub = Mock(return_value=self.sp_rpc_stub)

    def create_dummy_request(self, params={}, json_body={}):
        """
        @param params a dictionary of HTTP request parameters
        """
        request = testing.DummyRequest()
        request.params = params
        request.json_body = json_body
        request.translate = Mock()
        return request

    def spy(self, method):
        """
        Create a spy for the given method. Usage: method = spy(method)
        """
        return Mock(method, side_effect=method)


class NullServiceConnection():
    def do_rpc(self, bytes_to_send):
        # The Base64 string is the reply data for a Void reply from an RPC call
        return binascii.a2b_base64('CDpSAA==')
