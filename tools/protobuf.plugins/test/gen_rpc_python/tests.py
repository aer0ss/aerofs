#!/usr/bin/env python

import sys
import os
import unittest

sys.path.append(os.getcwd() + "/out")
from rpc_service_pb2 import Payload
from address_book_pb2 import *

"""
Address book service class.

Normally this is where the user would implement their callbacks. For the
purposes of this test, we hack together a reply and expect this hacked input to
come out the other end (in the TestProtobufRpcPlugin class).
"""
class AddressBookServiceImpl(AddressBookService):
    def __init__(self):
        self._id = 1

    def encode_error(self, msg):
        reply = ErrorReply()
        reply.errorMessage = str(msg)
        return reply

    """
    Mock add person function. Creates some fixed reply depending on the request.
    """
    def add_person(self, call):
        if call.person.name == "":
            reply = ErrorReply()
            reply.errorMessage = "Can't add a person with no name"
            t = 0
        else:
            reply = AddPersonReply()
            reply.id = self._id
            self._id += 1

        return reply

    def add_people(self, call):
        reply = AddPeopleReply();
        reply.length_name.extend([len(person.name) for person in call.people])
        return reply

"""
Connection service class used specifically for this test. This is where the user
would define their transport layer functionality (specifically in the do_rpc
function).

For the purposes of our test, we simply attach to the reactor directly (the
reactor, of course, usually resides on the server side).
"""
class TestConnectionService(object):
    def __init__(self):
        self._service = AddressBookServiceImpl()
        self._reactor = AddressBookServiceReactor(self._service)

    def do_rpc(self, bytes):
        result = self._reactor.react(bytes)
        return result

    def decode_error(self, error_message):
        return Exception(error_message.errorMessage)

"""
Main test class. Creates an address book rpc stub (the client) send some
messages through the channel, expecting some fixed reply for each given input
message as per the above address book service class.
"""
class TestProtobufRpcPlugin(unittest.TestCase):

    def setUp(self):
        self._service = AddressBookServiceRpcStub(TestConnectionService())

    def test_add_person_call(self):
        person = Person()
        person.name = 'AeroFS Dude'
        person.email = 'dude@aerofs.com'
        response = self._service.add_person(person, 'Some string')
        self.assertEqual(1, response.id)

    def test_add_empty_person(self):
        person = Person()
        try:
            response = self._service.add_person(person, '')
            self.assertTrue(False)
        except Exception as e:
            self.assertTrue(True)

    def test_add_people_call(self):
        p1 = Person()
        p2 = Person()
        p1.name = 'John'
        p2.name = 'Antonio'
        response = self._service.add_people([p1, p2], [])
        self.assertEqual(2, len(response.length_name))
        self.assertEqual(len(p1.name), response.length_name[0])
        self.assertEqual(len(p2.name), response.length_name[1])

    def test_invalid_send_data(self):
        try:
            # Calling private function directly to facilitate _send_data error.
            self._service._send_data(0, "hello world!")

            # Should never reach here.
            raise Exception("_send_data should have thrown.")

        except Exception, msg:
            self.assertEqual(str(msg), "Invalid RPC call.")

if __name__ == '__main__':
    unittest.main()
