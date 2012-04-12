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

      def do_rpc(self, bytes_to_send):
          return response_bytes

      def decode_error(self, errorProtobufMessage):
          return Exception

    bytes_to_send and response_bytes represent serialized
    Protobuf messages.
    '''
    self.connection = connectionService

  def _send_data(self, messageType, bytes):
    payload = rpc_service_pb2.Payload()
    payload.type = messageType
    payload.payload_data = bytes

    bytes_response = self.connection.do_rpc(payload.SerializeToString())
    reply = rpc_service_pb2.Payload.FromString(bytes_response)

    if reply.type == $ServiceName$RpcStub._METHOD_ENUMS_.$ErrorMethodEnum$:
      # Error
      error_message = $ErrorMethodOutputMessageType$()
      error_message.ParseFromString(reply.payload_data)
      raise self.connection.decode_error(error_message)

    return reply.payload_data

