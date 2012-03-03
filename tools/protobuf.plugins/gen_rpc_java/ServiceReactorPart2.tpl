        default:
          throw new com.google.protobuf.InvalidProtocolBufferException("Invalid RPC call: " + t);
      }
    } catch (Exception e) {
      com.google.common.util.concurrent.SettableFuture<$BaseMessageClass$> r = com.google.common.util.concurrent.SettableFuture.create();
      r.setException(e);
      reply = r;
    }
    final com.google.common.util.concurrent.SettableFuture<byte[]> future = com.google.common.util.concurrent.SettableFuture.create();

    final int finalCallType = callType;
    com.google.common.util.concurrent.Futures.addCallback(reply, new com.google.common.util.concurrent.FutureCallback<$BaseMessageClass$>()
    {
      @Override
      public void onSuccess($BaseMessageClass$ r)
      {
        com.aerofs.proto.RpcService.Payload p = com.aerofs.proto.RpcService.Payload.newBuilder()
          .setType(finalCallType)
          .setPayloadData(r.toByteString())
          .build();
        future.set(p.toByteArray());
      }

      @Override
      public void onFailure(Throwable error)
      {
        com.aerofs.proto.RpcService.Payload p = com.aerofs.proto.RpcService.Payload.newBuilder()
          .setType(ServiceRpcTypes.__ERROR__.ordinal())
          .setPayloadData(_service.encodeError(error).toByteString())
          .build();
        future.set(p.toByteArray());
      }
    });

    return future;
  }
}
