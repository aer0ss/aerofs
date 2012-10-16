#include "stdafx.h"
#include "AeroSocket.h"
#include "AsyncSocket.h"
#include "AeroFSShellExtension.h"
#include "../../gen/shellext.pb.h"
#include "logger.h"

#define LOCALHOST                 "127.0.0.1"
#define MAX_MESSAGE_LENGTH        1*1024*1024 // 1 MB

AeroSocket::AeroSocket()
	: m_socket(NULL),
	m_port(0)
{
}

AeroSocket::~AeroSocket()
{
	disconnect();
}

void AeroSocket::connect(unsigned short port)
{
	m_port = port;
	reconnect();
}

void AeroSocket::reconnect()
{
	if (!m_socket || m_socket->connectionState() != AsyncSocket::Disconnected) {
		m_socket = std::shared_ptr<AsyncSocket>(new AsyncSocket(shared_from_this()));
		m_socket->initialize();
	}

	m_socket->connect(LOCALHOST, m_port);
}

void AeroSocket::onSocketConnected()
{
	INFO_LOG("Connection successful");
	AeroFSShellExtension::instance()->sendGreeting();
	listenForMessage();
}

void AeroSocket::disconnect()
{
	if (m_socket) {
		m_socket->disconnect();
		m_socket.reset();
	}
}

void AeroSocket::forceDisconnect()
{
	if (m_socket) {
		m_socket->forceDisconnect();
		m_socket.reset();
	}
}

void AeroSocket::sendMessage(const ShellextCall& call)
{
	if (!m_socket) {
		return;
	}

	std::ostringstream output;

	// Write the length
	std::string msg = call.SerializeAsString();
	int size = htonl((u_long) msg.length());
	output.write((char*) &size, sizeof(size));

	// Write the data and send it
	output << msg;
	m_socket->send(output.str(), 0);
}

bool AeroSocket::isConnected() const
{
	if (m_socket) {
		return (m_socket->connectionState() == AsyncSocket::Connected);
	} else {
		return false;
	}
}

void AeroSocket::onSocketDisconnected(int error)
{
	INFO_LOG("Socket disconnected");
	disconnect();
}

void AeroSocket::listenForMessage()
{
	if (m_socket) {
		m_socket->receive(sizeof(int), ReadingLength);
	}
}

void AeroSocket::onSocketRead(const std::string& data, int state)
{
	unsigned int length;
	ShellextNotification notification;

	switch (state) {
	case ReadingLength:
		length = readInt(data);
		if (length == 0) {
			// LOG: invalid data
			disconnect();
			break;
		}
		if (m_socket) {
			m_socket->receive(length, ReadingData);
		}
		break;

	case ReadingData:
		if (!notification.ParseFromString(data)) {
			disconnect();
			break;
		}
		AeroFSShellExtension::instance()->parseNotification(notification);

		listenForMessage(); // Wait for the next message
		break;
	}
}

/**
Helper function to return the first 32 bits of a std::string as an unsigned int.
Returns 0 if there is any error
*/
unsigned int AeroSocket::readInt(const std::string& data) const
{
	if(data.length() < sizeof(int)) {
		return 0;
	}

	const int* pResult = (int*) data.c_str();
	if (pResult == NULL) {
		return 0;
	}

	return ntohl(*pResult);
}
