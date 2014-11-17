#include "stdafx.h"
#include "AeroSocket.h"
#include "AeroFSShellExtension.h"
#include "../../gen/shellext.pb.h"
#include "logger.h"
#include <windows.h>
#include <stdio.h>
#include <conio.h>
#include <tchar.h>

AeroSocket::AeroSocket()
	: m_handle(NULL),
	m_read_thread(NULL)
{
}

AeroSocket::~AeroSocket()
{
	disconnect();
}

void AeroSocket::connect(wstring& pipe_name)
{
	m_pipe_name = (wchar_t*)pipe_name.c_str();
    reconnect();
}

/**
* Reconnect and send greeting. Launch a thread to read.
*/
void AeroSocket::reconnect()
{
	if (m_handle == NULL || m_handle == INVALID_HANDLE_VALUE) {
		m_handle = CreateFile(
			m_pipe_name,                    // pipe name
			(GENERIC_READ | GENERIC_WRITE), // read and write access
			0,                              // no sharing
			NULL,                           // default security attributes
			OPEN_EXISTING,                  // opens existing pipe
			FILE_FLAG_OVERLAPPED,           // default attributes
			NULL);                          // no template file

		int last_error = GetLastError();

			if (m_handle == INVALID_HANDLE_VALUE) {
			ERROR_LOG("CreateFile returned an invalid handle value");
			return;
		}

		if (last_error != ERROR_SUCCESS && last_error != ERROR_PIPE_BUSY) {
			ERROR_LOG("CreateFile returned with err code" << last_error);
			return;
		}

		INFO_LOG("AeroFS Shellext connected to: " << m_pipe_name);
	}
	AeroFSShellExtension::instance()->sendGreeting();
	listenForMessage();
}

void AeroSocket::closeReadThread()
{
	if (m_read_thread != NULL) {
		CloseHandle(m_read_thread);
		TerminateThread(m_read_thread, 0);
		m_read_thread = NULL;
	}
}
void AeroSocket::disconnect()
{
	closeReadThread();
	if (m_handle != NULL) {
		INFO_LOG("===Disconnecting AeroSocket.===");
		CloseHandle(m_handle);
		m_handle = NULL;
	}
}

void AeroSocket::sendMessage(const ShellextCall& call)
{
	ostringstream output;

	// Write the length
	string msg = call.SerializeAsString();
	int size = htonl((u_long) msg.length());
	output.write((char*) &size, sizeof(size));

	// Write the data and send it
	output << msg;

	// WRITE
	writeFile(output);
}

void AeroSocket::writeFile(ostringstream& output)
{
	DWORD cbWritten;
	HANDLE write_event = CreateEvent(
		NULL,    // default security attribute
		TRUE,    // manual-reset event
		TRUE,    // initial state = signaled
		NULL);	 // unnamed event object

	if (write_event == NULL) {
		ERROR_LOG("Unable to CreateEvent for WriteFile. Exiting. Error code: " << write_event);
		return;
	}

	OVERLAPPED overlapped;
	overlapped.Offset = 0;
	overlapped.OffsetHigh = 0;
	overlapped.hEvent = write_event;

	string output_str = output.str();
	BOOL WINAPI success = WriteFile(
		m_handle,                   // pipe handle
		output_str.c_str(),         // message
		output_str.length(),        // message length
		&cbWritten,                 // bytes written
		&overlapped);               // overlapped

	int last_error = GetLastError();

	if (!success) {
		if (last_error == ERROR_IO_PENDING) {
			WaitForSingleObject(overlapped.hEvent, INFINITE);
			if (!GetOverlappedResult(m_handle, &overlapped, &cbWritten, false)) {
				ERROR_LOG("Overlapped WriteFile failed with err code: " << GetLastError());
			}
		} else {
			if (m_handle != NULL) {
				ERROR_LOG("WriteFile failed with err code: " << GetLastError());
			}
		}
	}
	CloseHandle(write_event);
}

bool AeroSocket::isConnected() const
{
	return (m_handle != NULL && m_handle != INVALID_HANDLE_VALUE);
}

void AeroSocket::listenForMessage()
{
	DWORD  dwThreadId = 0;
	if (m_read_thread == NULL) {
		m_read_thread = CreateThread(
			NULL,             // no security attribute
			0,                // default stack size
			ReadThread,       // thread proc
			(LPVOID) this,    // thread parameter
			0,                // not suspended
			&dwThreadId);	  // returns thread ID
		if (m_read_thread == NULL) {
			ERROR_LOG("Unable to create ReadThread. Err code: " << GetLastError());
			return;
		}
		CloseHandle(m_read_thread);
	}
}

DWORD WINAPI AeroSocket::ReadThread(LPVOID lpvParam)
{
	AeroSocket* self = (AeroSocket*) lpvParam;
	self->start_read();
	INFO_LOG("==========Exiting read thread==========");
	self->disconnect();
	return 1;
}

void AeroSocket::disconnectOnError(char* buf)
{
	cleanup_read_buffer(buf);
	disconnect();
}
void AeroSocket::start_read()
{
	while (true) {
		int length = sizeof(int);
		char* buf = new char[length];

		// Reading only int size at first because that represents the length of data to be read.
		if (!read_data(length, buf)) {
			disconnectOnError(buf);
			return;
		}

		// Extract the data length from the string buffer.
		string data = string(buf, length);
		length = readInt(data);
		cleanup_read_buffer(buf);

		if (length == -1) {
			disconnectOnError(buf);
			return;
		}

		// Now actually read the data.
		buf = new char[length];
		if(!read_data(length, buf)) {
			disconnectOnError(buf);
			return;
		}
		data = string(buf, length);
		handle_data(data);
		cleanup_read_buffer(buf);
	}
}

void AeroSocket::handle_data(string& data)
{
	ShellextNotification notification;
	if (!notification.ParseFromString(data)) {
		disconnect();
	}
	AeroFSShellExtension::instance()->parseNotification(notification);
}

/**
* Read data from the shellext windows named pipe. This function calls
* ReadFile.
*
* If ReadFile succeeds, it checks if all the data has been read.
* If all the data has been read, it copies the data to string buffer that
* is passed by reference to this function.
*
* If partial data has been read, it advances the buffer pointer and length
* variable by the bytes that have been read and loops again.
*
* If ReadFile doesn't succeed and the operation is pending, it waits
* till the opreation completes and performs the actions described above in
* case of complete and partial read respectively
*
* If none of the above two cases, ReadFile has failed. Womp Womp.
*/
bool AeroSocket::read_data(int length, char* buf)
{
	char *start_buf = buf;
	int start_length = length;
	DWORD cbRead;

	while (true && m_handle != NULL && m_handle != INVALID_HANDLE_VALUE) {
		HANDLE read_event = CreateEvent(
			NULL,    // default security attribute
			TRUE,    // manual-reset event
			TRUE,    // initial state = signaled
			NULL);   // unnamed event object

		if (read_event == NULL) {
			ERROR_LOG("Unable to CreateEvent for reading");
			return false;
		}

		OVERLAPPED overlapped;
		overlapped.Offset = 0;
		overlapped.OffsetHigh = 0;
		overlapped.hEvent = read_event;

		// Read the length of the data that needs to be read.
		BOOL WINAPI success = ReadFile(
			m_handle,       // pipe handle
			buf,            // buffer to receive reply
			length,         // size of buffer
			&cbRead,        // number of bytes read
			&overlapped);   // overlapped

		int last_error = GetLastError();
		if (success) {
			// Immediate sucess
			// Complete read
			if (cbRead == length) {
				CloseHandle(read_event);
				return true;
			} else {
				// Incomplete read.
				buf = buf + cbRead;
				length = length - cbRead;
				continue;
			}
		} else if (!success && (last_error == ERROR_IO_PENDING)) {
			WaitForSingleObject(overlapped.hEvent, INFINITE);
			BOOL WINAPI success = GetOverlappedResult(m_handle, &overlapped, &cbRead, false);
			if (success) {
				if (cbRead == length) {
					// Complete read
					CloseHandle(read_event);
					return true;
				} else {
					// Incomplete read.
					buf = buf + cbRead;
					length = length - cbRead;
					continue;
				}
			} else {
				// Check if handle is still valid i.e. its not been closed by the user.
				if (m_handle != NULL) {
					ERROR_LOG("Overlapped ReadFile failed. Error code: " << GetLastError());
				}
				CloseHandle(read_event);
				return false;
			}
		} else {
			ERROR_LOG("ReadFile failed. Error code: " << GetLastError());
			CloseHandle(read_event);
			return false;
		}
	}
}

void AeroSocket::cleanup_read_buffer(char* buf)
{
	delete [] buf;
	buf = NULL;
}

/**
Helper function to return the first 32 bits of a string as an unsigned int.
Returns 0 if there is any error
*/
unsigned int AeroSocket::readInt(const string& data) const
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
