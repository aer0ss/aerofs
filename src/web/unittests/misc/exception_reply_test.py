import unittest
from aerofs_common._gen.common_pb2 import PBException
from aerofs_common.exception import ExceptionReply

class ExceptionReplyTest(unittest.TestCase):

    def test_should_use_right_exception_type_names(self):
        """
        Because JavaScript refers these type names, common.proto should not
        change these names.
        TODO (WW) test actual JavaScripts?
        """

        pbe = PBException()
        pbe.type = PBException.NO_STRIPE_CUSTOMER_ID

        er = ExceptionReply(pbe)

        self.assertEquals(er.get_type_name(), "NO_STRIPE_CUSTOMER_ID")
