/**
  AeroSocket

  This class manages an AsyncSocket for the AeroFSShellExtension class.

  It provides a method to send PB message over the socket, and calls parseReply()
  on the AeroFSShellExtension class when it receives a PB message.
*/
// Indicates to the linker that Ws2_32.lib is needed, which is used by applications
// that use WinSock. We use the ntohl function which requires this library.
#pragma comment(lib, "Ws2_32.lib")
#pragma once

#include <memory>
#include <windows.h>
#include <stdio.h>
#include <conio.h>
#include <tchar.h>
#include <string>

using std::string;
using std::wstring;
using std::ostringstream;
using std::enable_shared_from_this;

class ShellextCall;
class ShellextNotification;

class AeroSocket
	: public enable_shared_from_this<AeroSocket>
{
public:
	AeroSocket();
	~AeroSocket();

	void connect(wstring& pipe_name);
	void sendMessage(const ShellextCall& call);
	void start_read();
	bool isConnected() const;
	void disconnect();

private:
	void cleanup_read_buffer(char* buf);
	bool read_data(int length, char* data);
	void handle_data(string& data);
	void writeFile(ostringstream& output);
	void reconnect();
	void listenForMessage();
	void disconnectOnError(char* buf);
	void closeReadThread();
	unsigned int readInt(const string& data) const;

private:
	static DWORD WINAPI ReadThread(LPVOID lpvParam);
	LPTSTR m_pipe_name;
	HANDLE m_handle;
	HANDLE m_read_thread;
};

