
public static class $ServiceName$Stub
{
  public interface $ServiceName$StubCallbacks
  {
    public com.google.common.util.concurrent.ListenableFuture<byte[]> doRPC(byte[] data);
    Throwable decodeError($ErrorReplyClass$ error);
  }

  $ServiceName$StubCallbacks _callbacks;

  public $ServiceName$Stub($ServiceName$StubCallbacks callbacks)
  {
    _callbacks = callbacks;
  }

  private <T extends $MessageType$> com.google.common.util.concurrent.ListenableFuture<T>
  sendQuery($ServiceClassName$Reactor.ServiceRpcTypes type, com.google.protobuf.ByteString bytes, $MessageType$.Builder b)
  {
    com.aerofs.proto.RpcService.Payload p = com.aerofs.proto.RpcService.Payload.newBuilder()
      .setType(type.ordinal())
      .setPayloadData(bytes)
      .build();

    com.google.common.util.concurrent.SettableFuture<T> receiveFuture = com.google.common.util.concurrent.SettableFuture.create();
    com.google.common.util.concurrent.ListenableFuture<byte[]> sendFuture = _callbacks.doRPC(p.toByteArray());
    com.google.common.util.concurrent.Futures.addCallback(sendFuture, new ReplyCallback<T>(receiveFuture, type, b));
    return receiveFuture;
  }

  private class ReplyCallback<T extends $MessageType$>
    implements com.google.common.util.concurrent.FutureCallback<byte[]>
  {
    private com.google.common.util.concurrent.SettableFuture<T> _replyFuture;
    private $ServiceClassName$Reactor.ServiceRpcTypes _replyType;
    private $MessageType$.Builder _builder;

    public ReplyCallback(com.google.common.util.concurrent.SettableFuture<T> future,
      $ServiceClassName$Reactor.ServiceRpcTypes type, $MessageType$.Builder builder)
    {
      _replyFuture = future;
      _replyType = type;
      _builder = builder;
    }

    @Override
    public void onSuccess(byte[] bytes)
    {
      try {
        com.aerofs.proto.RpcService.Payload p = com.aerofs.proto.RpcService.Payload.parseFrom(bytes);

        if (p.getType() == $ServiceClassName$Reactor.ServiceRpcTypes.__ERROR__.ordinal()) {

          $ErrorReplyClass$ error = $ErrorReplyClass$.parseFrom(p.getPayloadData());
          _replyFuture.setException(_callbacks.decodeError(error));
          return;
        }

        if (p.getType() != _replyType.ordinal()) {
          throw new RuntimeException("Unexpected response received from the server. Code: " + p.getType() + ". Expecting: " + _replyType.ordinal());
        }

        $MessageType$ r = _builder.mergeFrom(p.getPayloadData()).build();
        @SuppressWarnings("unchecked")
        T reply = (T) r;
        _replyFuture.set(reply);

      } catch (Throwable e) {
        _replyFuture.setException(e);
      }
    }

    @Override
    public void onFailure(Throwable throwable)
    {
      _replyFuture.setException(throwable);
    }
  }
