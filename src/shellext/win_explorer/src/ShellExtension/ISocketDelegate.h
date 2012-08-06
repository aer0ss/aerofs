/**
  ISocketDelegate

  Callback interface for the AsyncSocket

  Implement those methods to get notified about relevant events from the AsyncSocket

  Note: those callbacks will be called from the context of the socket's worker thread.
  Be sure to perform only thread-safe operations there.
*/

#pragma once

#include <string>

class ISocketDelegate
{
public:
	virtual ~ISocketDelegate() {}

	virtual void onSocketConnected() {}
	virtual void onSocketDisconnected(int error) {}
	virtual void onSocketRead(const std::string& data, int tag) {}
	virtual void onSocketWrote(int tag) {}
};