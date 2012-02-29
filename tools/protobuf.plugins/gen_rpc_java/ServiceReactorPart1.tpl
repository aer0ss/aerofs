public static class $ServiceName$Reactor
{
  $ServiceClassName$ _service;

  public enum ServiceRpcTypes {
    $EnumRpcTypes$
  }

  public $ServiceName$Reactor($ServiceClassName$ service)
  {
    _service = service;
  }

  public com.google.common.util.concurrent.ListenableFuture<byte[]> react(byte[] data) throws com.google.protobuf.InvalidProtocolBufferException
  {
    com.google.common.util.concurrent.ListenableFuture<? extends $BaseMessageClass$> reply;

    RpcService.Payload p = RpcService.Payload.parseFrom(data);

    ServiceRpcTypes t;
    try {
        t = ServiceRpcTypes.values()[p.getType()];
    } catch (ArrayIndexOutOfBoundsException ex) {
        throw new com.google.protobuf.InvalidProtocolBufferException("Unknown message type: " + p.getType() + ". Wrong protocol version.");
    }

    final ServiceRpcTypes type = t;
    switch (type) {
