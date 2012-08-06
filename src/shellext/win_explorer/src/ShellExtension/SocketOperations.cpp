#include "stdafx.h"
#include "SocketOperations.h"

#include <assert.h>
#include <mswsock.h>

#include "logger.h"
#include "ISocketDelegate.h"
#include "AsyncSocket.h"

SocketOperation::SocketOperation(OperationType type)
	: m_type(type),
	m_overlapped()
{
	m_overlapped.operation = this;
}

SocketOperation::SocketOperation(const SocketOperation& other)
	: m_type(other.m_type),
	m_overlapped()
{
	m_overlapped.operation = this;
}

SocketOperation::OperationType SocketOperation::type() const
{
	return m_type;
}

OVERLAPPED* SocketOperation::overlapped()
{
	return &m_overlapped.overlapped;
}

/*
 * Connect Operation
 *
 */

ConnectOperation::ConnectOperation(const std::weak_ptr<AsyncSocket>& asyncSocket)
	: SocketOperation(Connect),
	m_pAsyncSocket(asyncSocket)
{
}

void ConnectOperation::completed(const std::weak_ptr<ISocketDelegate>& pDelegate, int error, unsigned long count)
{
	if (error) {
		DEBUG_LOG("Connect operation failed with code " << error);
		// TODO: Call some error method on the delegate such as onConnectFailed()
		if (auto asyncSocket = m_pAsyncSocket.lock()) {
			asyncSocket->setState(AsyncSocket::Disconnected);
		}
		return;
	}

	if (auto asyncSocket = m_pAsyncSocket.lock()) {
		// This is required because we connect with ConnectEx
		int err = setsockopt(asyncSocket->socket(), SOL_SOCKET, SO_UPDATE_CONNECT_CONTEXT, NULL, 0);
		if (err) {
			DEBUG_LOG("Warning: setsockopt failed with code " << WSAGetLastError());
		}

		asyncSocket->setState(AsyncSocket::Connected);
	}

	if (auto del = pDelegate.lock()) {
		del->onSocketConnected();
	}
}

/*
 * Read Operation
 *
 */

ReadOperation::ReadOperation(SOCKET socket, int length, int tag)
	: SocketOperation(Read),
	m_socket(socket),
	m_length(length),
	m_tag(tag),
	m_buf(new char[length]),
	m_bytesRead(0)
{
}

ReadOperation::ReadOperation(const ReadOperation& other)
	: SocketOperation(other),
	m_socket(other.m_socket),
	m_length(other.m_length),
	m_tag(other.m_tag),
	m_buf(other.m_buf),
	m_bytesRead(other.m_bytesRead)
{
}

ReadOperation::~ReadOperation()
{
	delete m_buf;
}

bool ReadOperation::start()
{
	DWORD flags = 0;
	WSABUF b;
	b.buf = m_buf + m_bytesRead;
	b.len = m_length - m_bytesRead;

	int err = WSARecv(m_socket, &b, 1, NULL, &flags, this->overlapped(), NULL);
	if (err == SOCKET_ERROR && WSAGetLastError() != WSA_IO_PENDING) {
		return false;
	}
	return true;
}

void ReadOperation::completed(const std::weak_ptr<ISocketDelegate>& pDelegate, int error, unsigned long count)
{
	if (error) {
		DEBUG_LOG("Read operation failed with code " << error << " count " << count);
		if (auto del = pDelegate.lock()) {
			del->onSocketDisconnected(error);
		}
		return;
	}

	m_bytesRead += count;

	if (count > 0 && m_bytesRead < m_length) {
		// Partial read
		ReadOperation* op = new ReadOperation(*this);
		m_buf = NULL; // Make sure we don't delete the buffer, it is now owned by the new ReadOperation
		op->start();
	} else {
		// Full read
		if (auto del = pDelegate.lock()) {
			del->onSocketRead(std::string(m_buf, m_bytesRead), m_tag);
		}
	}

	// TODO: Call socket disconnected here
}

/*
 * Write Operation
 *
 */

WriteOperation::WriteOperation(const std::string& data, int tag)
	: SocketOperation(Write),
	m_buf(),
	m_tag(tag)
{
	assert(data.length() > 0);
	assert(data.length() < 64*1024);  // We arbitrarily decide that we should not send more than 64 KB data
	                                  // If that ever happens, something's certainly wrong somewhere

	m_buf.buf = new char[data.length()];
	memcpy(m_buf.buf, data.c_str(), data.length());
	m_buf.len = (ULONG) data.length();
}

WriteOperation::~WriteOperation()
{
	delete m_buf.buf;
}

void WriteOperation::completed(const std::weak_ptr<ISocketDelegate>& pDelegate, int error, unsigned long count)
{
	if (error) {
		DEBUG_LOG("Write operation failed with code " << error);
		return;
	}

	//DEBUG_LOG("Write operation completed. Wrote " << count << " bytes");
	if (auto del = pDelegate.lock()) {
		del->onSocketWrote(m_tag);
	}
}

WSABUF* WriteOperation::buf()
{
	return &m_buf;
}

/*
 * Disconnect Operation
 *
 */

DisconnectOperation::DisconnectOperation()
	: SocketOperation(Disconnect)
{
}

void DisconnectOperation::completed(const std::weak_ptr<ISocketDelegate>& pDelegate, int error, unsigned long count)
{
	if (auto del = pDelegate.lock()) {
		del->onSocketDisconnected(error);
	}
}