class $ServiceName$RpcStub(object):
  '''
  Auto-generated class that implements the method stubs
  of the service specified in the .proto file.
  '''

  class _METHOD_ENUMS_:
    $MethodEnums$

  def __init__(self, connectionService):
    '''
    Takes an object that
    has the following method signatures:

      def doRPC(self, bytes_to_send):
          return response_bytes

      def decodeError(self, errorProtobufMessage):
          return Exception

    bytes_to_send and response_bytes represent serialized
    Protobuf messages.
    '''
    self.connection = connectionService

  def _sendData(self, messageType, bytes):
    payload = rpc_service_pb2.Payload()
    payload.type = messageType
    payload.payload_data = bytes

    bytesResponse = self.connection.doRPC(payload.SerializeToString())
    reply = rpc_service_pb2.Payload.FromString(bytesResponse)

    if reply.type == $ServiceName$RpcStub._METHOD_ENUMS_.$ErrorMethodEnum$:
      # Error
      errorMessage = $ErrorMethodOutputMessageType$()
      errorMessage.ParseFromString(reply.payload_data)
      raise self.connection.decodeError(errorMessage)

    return reply.payload_data

