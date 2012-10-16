#include "stdafx.h"
#include "AsyncSocket.h"

#include <process.h>
#include <assert.h>
#include <sstream>
#include <ws2tcpip.h>
#include <mswsock.h> // for ConnectEx

#include "logger.h"
#include "SocketOperations.h"

#pragma comment(lib,"ws2_32.lib")

#define NUMBER_OF_THREADS 1
#define THREAD_WAIT_TIMEOUT 3000 // give the worker thread 3 seconds to finish

LPFN_CONNECTEX ConnectEx = NULL;

AsyncSocket::AsyncSocket(const std::weak_ptr<ISocketDelegate>& pDelegate)
	: m_socket(INVALID_SOCKET),
	m_hThread(NULL),
	m_threadId(NULL),
	m_hIocp(NULL),
	m_state(Uninitialized),
	m_delegate(pDelegate)
{
}

void AsyncSocket::initialize()
{
	assert(m_state == Uninitialized);

	// Initialize Winsock
	WSADATA wsaData;
	WSAStartup(MAKEWORD(2,2), &wsaData);

	// Create the socket
	m_socket = WSASocket(AF_INET, SOCK_STREAM, IPPROTO_TCP, NULL, 0, WSA_FLAG_OVERLAPPED);
	if (m_socket == INVALID_SOCKET) {
		terminate(L"WSASocket failed. Error: " + WSAGetLastError());
		return;
	}

	// Create the IOCP port and associate it with the socket
	m_hIocp = CreateIoCompletionPort((HANDLE)m_socket, NULL, (ULONG_PTR)0, NUMBER_OF_THREADS);
	if (m_hIocp == NULL) {
		terminate(L"CreateIoCompletionPort failed. Error: " + GetLastError());
		return;
	}

	std::shared_ptr<AsyncSocket>* pThis(new std::shared_ptr<AsyncSocket>(shared_from_this()));
	m_hThread = (HANDLE) _beginthreadex(NULL, 0, threadRun, (void*) pThis, 0, NULL);
	if (!m_hThread) {
		delete pThis;
	}

	// Get a pointer to the ConnectEx function
	GUID guidConnectEx = WSAID_CONNECTEX;
	DWORD dwBytes; // TODO: Check if we can get rid of that and just pass NULL
	int err = WSAIoctl(m_socket, SIO_GET_EXTENSION_FUNCTION_POINTER, &guidConnectEx, sizeof(guidConnectEx), &ConnectEx, sizeof(ConnectEx), &dwBytes, NULL, NULL);
	if (err == SOCKET_ERROR) {
		terminate(L"WSAIoctl failed. Error: " + WSAGetLastError());
		return;
	}

	// Bind the socket to a random port as required by ConnectEx
	sockaddr_in connectAddress;
	connectAddress.sin_family = AF_INET;
	connectAddress.sin_addr.s_addr = INADDR_ANY;
	connectAddress.sin_port = 0;

	if (bind(m_socket, (sockaddr*) &connectAddress, sizeof(connectAddress))) {
		terminate(L"Unable to bind to port. Error: " + WSAGetLastError());
		return;
	}

	m_state = Disconnected;
}

AsyncSocket::~AsyncSocket()
{
	terminate();
	WSACleanup();
	DEBUG_LOG("AsyncSocket destroyed");
}

/**
Prints an optional error message and puts the AsyncSocket into terminated state.
A terminated AsyncSocket may not be used again.
This function is thread-safe
*/
void AsyncSocket::terminate(const std::wstring& msg)
{
	if (!msg.empty()) {
		ERROR_LOG("## AsyncSocket error: " << msg);
	}

	m_state = Terminated;

	if (m_socket) {
		shutdown(m_socket, SD_BOTH);
		closesocket(m_socket);
		m_socket = NULL;
	}

	if (m_hIocp) {
		CloseHandle(m_hIocp); // this will close the worker thread
		m_hIocp = NULL;
	}

	if (m_hThread) {
		// We need to wait for the worker thread to finish, otherwise we might free a pending operation
		// while it's busy. But make sure we don't wait if this method is called from the worker thread,
		// otherwise we would deadlock.
		if (GetCurrentThreadId() != m_threadId) {
			DWORD result = WaitForSingleObject(m_hThread, THREAD_WAIT_TIMEOUT);
			if (result != WAIT_OBJECT_0) {
				// Wait timed out, forcefully close the thread
				TerminateThread(m_hThread, 0);
			}
		}

		CloseHandle(m_hThread);
		m_hThread = NULL;
		m_threadId = NULL;
	}
}

AsyncSocket::ConnectionState AsyncSocket::connectionState() const
{
	return m_state;
}

void AsyncSocket::connect(const std::string& host, unsigned short port)
{
	if (m_state != Disconnected) {
		return;
	}

	m_state = Connecting;

	// Resolve the host and set the port

	ADDRINFO* addr = NULL;
	ADDRINFO hints = {};
	hints.ai_family = AF_INET;
	hints.ai_socktype = SOCK_STREAM;
	hints.ai_protocol = IPPROTO_TCP;

	std::stringstream strPort;
	strPort << port;

	int err = getaddrinfo(host.c_str(), strPort.str().c_str(), &hints, &addr);
	if (err) {
		terminate(L"getaddrinfo failed. Error: " + WSAGetLastError());
		return;
	}

	ConnectOperation* op = new ConnectOperation(shared_from_this());
	BOOL success = ConnectEx(m_socket, addr->ai_addr, (int) addr->ai_addrlen, NULL, 0, 0, op->overlapped());
	int lastErr = WSAGetLastError();
	freeaddrinfo(addr);

	// TODO: should return bool here
	if (!success && lastErr != WSA_IO_PENDING) {
		delete op;
		terminate(L"ConnectEx failed. Error: " + lastErr);
		return;
	}
}

void AsyncSocket::disconnect()
{
	if (m_state == Terminated) {
		return;
	}

	m_state = Disconnected;

	shutdown(m_socket, SD_SEND);
	DisconnectOperation* op = new DisconnectOperation();
	PostQueuedCompletionStatus(m_hIocp, 0, 0, op->overlapped());
}

void AsyncSocket::forceDisconnect()
{
	if (m_state == Terminated) {
		return;
	}

	terminate();
}

void AsyncSocket::send(const std::string& data, int tag)
{
	if (m_state == Terminated) {
		return;
	}

	// Proceed with the send operation in other non-valid states (such as
	// Disconnected) so that the user gets a proper error callback

	WriteOperation* op = new WriteOperation(data, tag);

	int err = WSASend(m_socket, op->buf(), 1, NULL, 0, op->overlapped(), NULL);
	if (err == SOCKET_ERROR && WSAGetLastError() != WSA_IO_PENDING) {
		delete op;
		terminate(L"Error: send failed. WSAError: " + WSAGetLastError());
	}
}

void AsyncSocket::receive(int length, int tag)
{
	if (m_state == Terminated) {
		return;
	}

	ReadOperation* op = new ReadOperation(m_socket, length, tag);

	if (!op->start()) {
		delete op;
		terminate(L"Error: receive failed. WSAError: " + WSAGetLastError());
	}
}

unsigned AsyncSocket::threadRun(void* param)
{
	DEBUG_LOG("Begin thread");

	auto ppAsyncSocket = reinterpret_cast<std::shared_ptr<AsyncSocket>*>(param);

	(*ppAsyncSocket)->m_threadId = GetCurrentThreadId();
	HANDLE hIocp = (*ppAsyncSocket)->m_hIocp;
	std::weak_ptr<ISocketDelegate> pDelegate((*ppAsyncSocket)->m_delegate);

	delete ppAsyncSocket;
	ppAsyncSocket = NULL;

	while (true) {

		// Wait until an overlapped operations completes
		OVERLAPPED* overlapped;
		DWORD bytesCount;
		ULONG_PTR completionKey;
		BOOL success = GetQueuedCompletionStatus(hIocp, &bytesCount, &completionKey, &overlapped, INFINITE);
		int err = success ? 0 : GetLastError();

		// Get a pointer to the SocketOperation that just completed
		SocketOperation* operation = NULL;
		if (overlapped) {
			OVERLAPPEDPLUS* overlappedPlus = CONTAINING_RECORD(overlapped, OVERLAPPEDPLUS, overlapped);
			operation = overlappedPlus->operation;
		}

		if (!success && !operation) {
			break;
		}

		// Process the operation
		if (operation) {
			operation->completed(pDelegate, err, bytesCount);
			delete operation;
		}
	}

	DEBUG_LOG("Thread closed");

	return 0;
}

/**
TODO: should this be made thread safe?
*/
void AsyncSocket::setState(ConnectionState state)
{
	m_state = state;
}

/*
Thread-safe because socket is constant for the lifetime of AsyncSocket
*/
SOCKET AsyncSocket::socket() const
{
	return m_socket;
}