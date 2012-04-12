def $MethodName$($MethodArgs$):
  m = $InputMessageType$()
  $MessageFieldAssignment$
  d = self._send_data($ServiceName$RpcStub._METHOD_ENUMS_.$MethodCallEnum$, m.SerializeToString())
  return $OutputMessageType$.FromString(d)
