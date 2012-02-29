      default:
        throw new com.google.protobuf.InvalidProtocolBufferException("");
    }

    final com.google.common.util.concurrent.SettableFuture<byte[]> future = com.google.common.util.concurrent.SettableFuture.create();

    com.google.common.util.concurrent.Futures.addCallback(reply, new com.google.common.util.concurrent.FutureCallback<$BaseMessageClass$>()
    {
      @Override
      public void onSuccess($BaseMessageClass$ r)
      {
        RpcService.Payload p = RpcService.Payload.newBuilder()
          .setType(type.ordinal())
          .setPayloadData(r.toByteString())
          .build();
        future.set(p.toByteArray());
      }

      @Override
      public void onFailure(Throwable throwable)
      {
        future.setException(throwable);
      }
    });

    return future;
  }
}
