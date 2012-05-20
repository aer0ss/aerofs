class $ServiceName$Reactor(object):
  """
  Auto-generated reactor class. Used server-side to react to bytes received on
  the protobuf communication channel.
  """

  class _METHOD_ENUMS_:
    $MethodEnums$

  """
  Constructor. The service instance passed in must inherit from the abstract
  class $ServiceName$.
  """
  def __init__(self, service):
    self._service = service

  def react(self, received_bytes):

    payload = Payload.FromString(received_bytes)
    t = payload.type

    reply_payload = Payload()
    reply_payload.type = t

    try:
