      else:
        raise Exception("Invalid RPC call.")

    except Exception, msg:
      reply_payload.type = $ServiceName$Reactor._METHOD_ENUMS_.$ErrorMethodName$
      reply = self._service.encode_error(msg)

    reply_payload.payload_data = reply.SerializeToString()
    return reply_payload.SerializeToString()
