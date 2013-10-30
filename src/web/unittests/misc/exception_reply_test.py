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

        map = {
            PBException.NO_STRIPE_CUSTOMER_ID: "NO_STRIPE_CUSTOMER_ID",
            PBException.NO_ADMIN_OR_OWNER: "NO_ADMIN_OR_OWNER",
            PBException.NOT_AUTHENTICATED: "NOT_AUTHENTICATED",
            PBException.SHARED_FOLDER_RULES_EDITORS_DISALLOWED_IN_EXTERNALLY_SHARED_FOLDER:
                "SHARED_FOLDER_RULES_EDITORS_DISALLOWED_IN_EXTERNALLY_SHARED_FOLDER",
            PBException.SHARED_FOLDER_RULES_WARNING_ADD_EXTERNAL_USER:
                "SHARED_FOLDER_RULES_WARNING_ADD_EXTERNAL_USER",
            PBException.SHARED_FOLDER_RULES_WARNING_OWNER_CAN_SHARE_WITH_EXTERNAL_USERS:
                "SHARED_FOLDER_RULES_WARNING_OWNER_CAN_SHARE_WITH_EXTERNAL_USERS"
        }

        for i in map:
            pbe = PBException()
            pbe.type = i
            er = ExceptionReply(pbe)
            self.assertEquals(er.get_type_name(), map[i])
