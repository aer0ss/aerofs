import unittest
from pyramid.httpexceptions import HTTPBadRequest
from web.error import error

class ErrorTest(unittest.TestCase):

    def test_should_normalize_empty_message(self):
        try:
            error("")
        except HTTPBadRequest as e:
            self.assertEqual(e.body, '{"message": "", "type": "unspecified"}')


    def test_should_normalize_as_expected(self):
        """
        The method should:
            1) capitalize the first letter.
            2) retain caplitalization of the rest of the message
            3) add a period if necessary
        """
        try:
            error("viktor FranKl")
        except HTTPBadRequest as e:
            self.assertEqual(e.body, '{"message": "Viktor FranKl.", "type": "unspecified"}')

    def test_should_not_add_period_if_unnecessary(self):
        try:
            error("Test?")
        except HTTPBadRequest as e:
            self.assertEqual(e.body, '{"message": "Test?", "type": "unspecified"}')