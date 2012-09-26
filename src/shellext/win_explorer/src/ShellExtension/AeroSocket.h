/**
  AeroSocket

  This class manages an AsyncSocket for the AeroFSShellExtension class.

  It provides a method to send PB message over the socket, and calls parseReply()
  on the AeroFSShellExtension class when it receives a PB message.
*/

#pragma once

#include "ISocketDelegate.h"
#include <memory>

class AsyncSocket;
class ShellextCall;
class ShellextNotification;

class AeroSocket
	: public ISocketDelegate,
	public std::enable_shared_from_this<AeroSocket>
{
public:
	AeroSocket();
	~AeroSocket();

	void connect(unsigned short port);
	void sendMessage(const ShellextCall& call);
	bool isConnected() const;
	void disconnect();

private:
	// ISocketDelegate interface
	void onSocketConnected();
	void onSocketDisconnected(int error);
	void onSocketRead(const std::string& data, int tag);

	void reconnect();
	typedef enum { ReadingLength, ReadingData } ReadingState;
	void listenForMessage();
	void parseReply(const ShellextNotification& reply);

	unsigned int readInt(const std::string& data) const;

private:
	std::shared_ptr<AsyncSocket> m_socket;
	unsigned short m_port;
};

