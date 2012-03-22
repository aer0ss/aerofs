def $MethodName$($MethodArgs$):
  m = $InputMessageType$()
  $MessageFieldAssignment$
  d = self._sendData($ServiceName$RpcStub._METHOD_ENUMS_.$MethodCallEnum$, m.SerializeToString())
  return $OutputMessageType$.FromString(d)
