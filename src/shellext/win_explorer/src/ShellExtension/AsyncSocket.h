/**
  AsyncSocket

  This class provides an asynchronous interface to the WinSock API.
  Its design is loosely modeled after CocoaAsyncSocket.

  It provides a set of asynchronous methods to operate the socket (connect, send, etc...),
  and a ISocketDelegate interface that the user must implement to receive notifications about
  the completion of the task.

  The delegate is passed to the constructor of this class as a weak pointer reference. This way,
  if the delegate is destroyed while operations are pending, no crash will occur. The callbacks
  will simply be ignored.
*/

#pragma once

#include <winsock2.h>
#include <string>
#include <memory>

class ISocketDelegate;

class AsyncSocket : public std::enable_shared_from_this<AsyncSocket>
{
public:
	AsyncSocket(const std::weak_ptr<ISocketDelegate>& pDelegate);
	virtual ~AsyncSocket();

public:
	typedef enum { Uninitialized, Disconnected, Connecting, Connected, Terminated } ConnectionState;

	void initialize();
	void connect(const std::string& host, unsigned short port);
	void disconnect();
	void send(const std::string& data, int tag);
	void receive(int length, int tag);
	ConnectionState connectionState() const;

private:
	friend class ConnectOperation;
	friend class DisconnectOperation;
	static unsigned __stdcall threadRun(void* params);
	void terminate(const std::wstring& msg = L"");
	void setState(ConnectionState state);
	SOCKET socket() const;

private:
	HANDLE m_hThread;
	DWORD m_threadId;
	HANDLE m_hIocp;
	SOCKET m_socket;
	ConnectionState m_state;
	std::weak_ptr<ISocketDelegate> const m_delegate;
};
