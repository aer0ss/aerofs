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

	def doRPC(self, bytes):
		payload = Payload.FromString(bytes)
		call = AddPersonCall.FromString(payload.payload_data)
		t = payload.type
		if call.person.name == "":
			reply = ErrorReply()
			reply.errorMessage = "Can't add a person with no name"
			t = 0
		else:
			reply = AddPersonReply()
			reply.id = self._id
			self._id += 1
		replyPayload = Payload()
		replyPayload.type = t
		replyPayload.payload_data = reply.SerializeToString()
		return replyPayload.SerializeToString()

	def decodeError(self, errorMessage):
		return Exception(errorMessage)

class TestProtobufRpcPlugin(unittest.TestCase):

	def setUp(self):
		self.service = AddressBookServiceRpcStub(TestAddPersonConnection())

	def test_addPersonCall(self):
		person = Person()
		person.name = 'AeroFS Dude'
		person.email = 'dude@aerofs.com'
		response = self.service.AddPerson(person, 'Some string')
		self.assertEqual(1, response.id)

	def test_addEmptyPerson(self):
		person = Person()
		try:
			response = self.service.AddPerson(person, '')
			self.assertTrue(False)
		except Exception as e:
			self.assertTrue(True)

if __name__ == '__main__':
	unittest.main()
