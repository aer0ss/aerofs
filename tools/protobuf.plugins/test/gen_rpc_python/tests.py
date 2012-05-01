#!/usr/bin/env python

import sys
import os
import unittest

sys.path.append(os.getcwd() + "/out")
from rpc_service_pb2 import Payload
from address_book_pb2 import *

class TestAddPersonConnection(object):
    def __init__(self):
        self._id = 1

    def do_rpc(self, bytes):
        ADD_PERSON_CALL = 1
        ADD_PEOPLE_CALL = 2

        payload = Payload.FromString(bytes)
        t = payload.type

        if t == ADD_PERSON_CALL:
            call = AddPersonCall.FromString(payload.payload_data)
            if call.person.name == "":
                reply = ErrorReply()
                reply.errorMessage = "Can't add a person with no name"
                t = 0
            else:
                reply = AddPersonReply()
                reply.id = self._id
                self._id += 1

        elif t == ADD_PEOPLE_CALL:
            call = AddPeopleCall.FromString(payload.payload_data)
            reply = AddPeopleReply();
            reply.length_name.extend([len(person.name) for person in call.people])

        replyPayload = Payload()
        replyPayload.type = t
        replyPayload.payload_data = reply.SerializeToString()
        return replyPayload.SerializeToString()

    def decode_error(self, error_message):
        return Exception(error_message)

class TestProtobufRpcPlugin(unittest.TestCase):

    def setUp(self):
        self.service = AddressBookServiceRpcStub(TestAddPersonConnection())

    def test_add_person_call(self):
        person = Person()
        person.name = 'AeroFS Dude'
        person.email = 'dude@aerofs.com'
        response = self.service.add_person(person, 'Some string')
        self.assertEqual(1, response.id)

    def test_add_empty_person(self):
        person = Person()
        try:
            response = self.service.add_person(person, '')
            self.assertTrue(False)
        except Exception as e:
            self.assertTrue(True)

    def test_add_people_call(self):
        p1 = Person()
        p2 = Person()
        p1.name = 'John'
        p2.name = 'Antonio'
        response = self.service.add_people([p1, p2], [])
        self.assertEqual(2, len(response.length_name))
        self.assertEqual(len(p1.name), response.length_name[0])
        self.assertEqual(len(p2.name), response.length_name[1])

if __name__ == '__main__':
    unittest.main()
