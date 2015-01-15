/**
  AeroFSShellExtension
  This is the central class of our shell extension.

  It's a singleton that exists as long as our extension is loaded in memory.
  We can get a pointer to this class from anywhere in the code by using static_cast<AeroFSShellExtension*>(ATL::_pAtlModule);

  Its role is to communicate both with the AeroFS gui and with the ContextMenu and IconOverlay code.
*/
#pragma once

#include "../../../common/OverlayCache.h"

#include <string>
#include <memory>
#include <set>

using std::wstring;
using std::shared_ptr;
using std::string;

#ifdef _UNICODE
	typedef std::wstring tstring;
#else
	typedef std::string tstring;
#endif


class AeroSocket;
class OverlayCache;

class ShellextNotification;
class PathStatusNotification;

enum Overlay {
	O_None,
	O_InSync,
	O_OutSync,
	O_Uploading,
	O_Downloading,
	O_Conflict
};

// flags are bitwise OR'ed
enum PathFlag {
	RootAnchor = 1 << 0,
	Directory  = 1 << 1
};

class AeroFSShellExtension : public ATL::CAtlDllModuleT<AeroFSShellExtension>
                           , public OverlayCache::EvictionDelegate
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
	void showConflictResolutionDialog(const std::wstring& path);
	void sendGreeting();
	Overlay overlay(const std::wstring& path);

	static AeroFSShellExtension* instance();

private:
	wstring getSocketFileName();
	void reconnect();
	void setRootAnchor(const std::string& path);
	void setUserId(const std::string& user);
	void onPathStatusNotification(const PathStatusNotification& fstatus);
	void clearCache();
	void refreshShellItem(const std::wstring& path, bool recursive) const;

	virtual void evicted(const std::wstring& key, int value) const;

private:
	CRITICAL_SECTION m_cs;
	shared_ptr<AeroSocket> m_socket;
	shared_ptr<OverlayCache> m_cache;
	wstring m_rootAnchor;
	wstring m_userId;
	unsigned long m_lastConnectionAttempt;
	tstring m_fileName;
	bool is_link_sharing_enabled;
};
