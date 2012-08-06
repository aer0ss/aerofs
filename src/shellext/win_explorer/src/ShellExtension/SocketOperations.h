#pragma once

#include <string>
#include <memory>
#include <winsock2.h>

class SocketOperation;
class ISocketDelegate;
class AsyncSocket;

/**
  This structure keeps a pointer to our SocketOperation alongside with the
  OVERLAPPED structure so that we can retrieve the operation upon completion
  of an overlapped winsock call.
*/
typedef struct {
	OVERLAPPED overlapped;
	SocketOperation* operation;
} OVERLAPPEDPLUS;

/**
  Abstract base class for all socket operations (read, write, disconnect)
*/
class SocketOperation
{
public:
	typedef enum {Connect, Read, Write, Disconnect} OperationType;
	SocketOperation(OperationType type);
	SocketOperation(const SocketOperation& other);
	virtual ~SocketOperation() {};

	OperationType type() const;
	OVERLAPPED* overlapped();

	/**
	  This method is called by the worker thread when the operation completes
	  @param delegate: a weak pointer to an ISocketDelegate instance
	  @param error: 0 is no error, otherwise the error code from GetLastError()
	  @param count: the number of bytes affected by the operation
	*/
	virtual void completed(const std::weak_ptr<ISocketDelegate>& pDelegate, int error, unsigned long count) = 0;

private:
	SocketOperation() {}
	OperationType m_type;
	OVERLAPPEDPLUS m_overlapped;
};

class ConnectOperation : public SocketOperation
{
public:
	ConnectOperation(const std::weak_ptr<AsyncSocket>& asyncSocket);
	void completed(const std::weak_ptr<ISocketDelegate>& pDelegate, int error, unsigned long count);

private:
	std::weak_ptr<AsyncSocket> m_pAsyncSocket;
};

class ReadOperation : public SocketOperation
{
public:
	ReadOperation(SOCKET socket, int length, int tag);
	ReadOperation(const ReadOperation& other);
	~ReadOperation();

	bool start();
	void completed(const std::weak_ptr<ISocketDelegate>& pDelegate, int error, unsigned long count);

private:
	const SOCKET m_socket;
	const unsigned int m_length;
	const int m_tag;
	char* m_buf;
	unsigned int m_bytesRead;
};

class WriteOperation : public SocketOperation
{
public:
	WriteOperation(const std::string& data, int tag);
	~WriteOperation();

	WSABUF* buf();
	void completed(const std::weak_ptr<ISocketDelegate>& pDelegate, int error, unsigned long count);

private:
	WSABUF m_buf;
	const int m_tag;
};

class DisconnectOperation : public SocketOperation
{
public:
	DisconnectOperation();
	void completed(const std::weak_ptr<ISocketDelegate>& pDelegate, int error, unsigned long count);
};
