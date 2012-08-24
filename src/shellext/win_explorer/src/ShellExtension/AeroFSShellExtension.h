/**
  AeroFSShellExtension
  This is the central class of our shell extension.

  It's a singleton that exists as long as our extension is loaded in memory.
  We can get a pointer to this class from anywhere in the code by using static_cast<AeroFSShellExtension*>(ATL::_pAtlModule);

  Its role is to communicate both with the AeroFS gui and with the ContextMenu and IconOverlay code.
*/
#pragma once

#include <string>
#include <memory>
#include <set>

class AeroSocket;
class AeroNode;

class ShellextNotification;
class FileStatusNotification;

// flags are bitwise OR'ed
enum PathFlag {
	RootAnchor = 1 << 0,
	Directory  = 1 << 1
};

class AeroFSShellExtension : public ATL::CAtlDllModuleT<AeroFSShellExtension>
{
public:
	AeroFSShellExtension();
	~AeroFSShellExtension();

	void cleanup();
	bool isConnectedToGUI();
	bool isUnderRootAnchor(const std::wstring& path);
	bool shouldEnableTestingFeatures() const;
	int pathFlags(const std::wstring& path) const;
	void parseNotification(const ShellextNotification& reply);
	void showSyncStatusDialog(const std::wstring& path);
	void showVersionHistoryDialog(const std::wstring& path);
	void showShareFolderDialog(const std::wstring& path);
	void sendGreeting();
	AeroNode* rootNode() const;

	static AeroFSShellExtension* instance();

private:
	unsigned short readPortNumber();
	void reconnect();
	void setRootAnchor(const std::string& path);
	void setUserId(const std::string& user);
	void onFileStatusNotification(const FileStatusNotification& fstatus);
	void clearCache();

private:
	CRITICAL_SECTION m_cs;
	std::shared_ptr<AeroSocket> m_socket;
	std::wstring m_rootAnchor;
	std::wstring m_userId;
	unsigned long m_lastConnectionAttempt;
	unsigned short m_port;
	AeroNode* m_rootNode;
};
