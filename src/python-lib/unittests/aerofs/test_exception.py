import unittest
from aerofs_common import exception

from aerofs_common._gen import common_pb2

class RegularMessagePBExceptionTest(unittest.TestCase):
    def runTest(self):
        pbException = common_pb2.PBException()
        pbException.type = common_pb2.PBException.INTERNAL_ERROR
        pbException.message = "This is an error"

        reply = exception.ExceptionReply(pbException)
        self.assertEqual(common_pb2.PBException.INTERNAL_ERROR, reply.get_type())
        self.assertEqual(u"INTERNAL_ERROR: This is an error", str(reply))

class PlainTextMessagePBExceptionTest(unittest.TestCase):
    def runTest(self):
        pbException = common_pb2.PBException()
        pbException.type = common_pb2.PBException.INTERNAL_ERROR
        pbException.message = "This is an error"
        pbException.plain_text_message = "This is a pretty string"

        reply = exception.ExceptionReply(pbException)
        self.assertEqual(common_pb2.PBException.INTERNAL_ERROR, reply.get_type())
        self.assertEqual(u"INTERNAL_ERROR: This is a pretty string", str(reply))
