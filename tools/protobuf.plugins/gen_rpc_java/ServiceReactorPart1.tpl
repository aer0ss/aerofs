
public static class $ServiceName$Reactor
{
  $ServiceInterface$ _service;

  public enum ServiceRpcTypes {
    $EnumRpcTypes$
  }

  public $ServiceName$Reactor($ServiceInterface$ service)
  {
    _service = service;
  }

  public com.google.common.util.concurrent.ListenableFuture<byte[]> react(byte[] data)
  {
    com.google.common.util.concurrent.ListenableFuture<? extends $BaseMessageClass$> reply;

    int callType;
    try {
      com.aerofs.proto.RpcService.Payload p = com.aerofs.proto.RpcService.Payload.parseFrom(data);
      callType = p.getType();

      ServiceRpcTypes t;
      try {
        t = ServiceRpcTypes.values()[callType];
      } catch (ArrayIndexOutOfBoundsException ex) {
        throw new com.google.protobuf.InvalidProtocolBufferException("Unknown message type: " + callType + ". Wrong protocol version.");
      }

      switch (t) {
