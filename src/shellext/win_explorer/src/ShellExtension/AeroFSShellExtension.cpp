#include "stdafx.h"
#include "AeroFSShellExtension.h"

#include <string>
#include <regex>
#include <fstream>
#include <shlobj.h>
#include <Lmcons.h>
#include <tchar.h>

#include "AeroSocket.h"
#include "logger.h"
#include "string_helpers.h"
#include "../../gen/shellext.pb.h"

#include "../../../common/common.h"

#define DELAY_BEFORE_CONNECTION_RETRY 2000 // milliseconds
#define OVERLAY_CACHE_SIZE_LIMIT 10000 // max number of path for which overlay state is cached
#define PIPE_NAMESPACE L"\\\\.\\pipe\\"
#define SOCKET_FILE_NAME_SUFFIX L"_shellext_single" // No shellext for TS. Can hardcode single.

AeroFSShellExtension _instance;

AeroFSShellExtension::AeroFSShellExtension()
	: m_socket(new AeroSocket()),
	m_cache(new OverlayCache(OVERLAY_CACHE_SIZE_LIMIT)),
	m_rootAnchor(),
	m_lastConnectionAttempt(0),
	m_fileName(),
	m_is_link_sharing_enabled(false)
{
	InitializeCriticalSection(&m_cs);
}

AeroFSShellExtension::~AeroFSShellExtension()
{
	DeleteCriticalSection(&m_cs);
}

/**
We call this during DllCanUnloadNow() to perform de-initialization of the shell extension
We should not do heavy de-initilization in the destructor
*/
void AeroFSShellExtension::cleanup()
{
	m_socket->disconnect();
}

AeroFSShellExtension* AeroFSShellExtension::instance()
{
	return &_instance;
}

/**
Returns true if we are connected to the GUI and we have received the AnchorRoot message.
If we are not connected, and it has been n milliseconds since our last connection attempt,
it will asynchronously try to reconnect now.

(the retry delay is specified by DELAY_BEFORE_CONNECTION_RETRY)

This method is thread-safe.
*/
bool AeroFSShellExtension::isConnectedToGUI()
{
	// We are connected to GUI iff the socket is connected and the anchor root has been set
	if (m_socket->isConnected() && m_rootAnchor.length() > 0) {
		return true;
	}
	EnterCriticalSection(&m_cs);

	// If the socket is disconnected, try to reconnect if we haven't already tried in the last X ms
	if (!m_socket->isConnected() && GetTickCount() - m_lastConnectionAttempt > DELAY_BEFORE_CONNECTION_RETRY) {
		reconnect();
	}
	LeaveCriticalSection(&m_cs);

	return false;
}

void AeroFSShellExtension::reconnect()
{
	if (m_fileName.empty()) {
		m_fileName = getSocketFileName();
	}
	INFO_LOG("Trying to connect to shellext file: " << m_fileName.c_str());
	m_lastConnectionAttempt = GetTickCount();
	m_socket->connect(m_fileName);
}

/**
 * Returns true if and only if: path is under root anchor
 * Note: the test is inclusive (i.e returns true for root anchor)
 */
bool AeroFSShellExtension::isUnderRootAnchor(const std::wstring& path)
{
	std::wstring lpath = lowercase(path);
	if (lpath.empty()
			|| m_rootAnchor.empty()
			|| lpath.length() < m_rootAnchor.length()) {
		return false;
	}

	// Check that path starts with root anchor
	return str_starts_with(lpath, m_rootAnchor) &&
		(lpath.length() == m_rootAnchor.length() || lpath.at(m_rootAnchor.length()) == '\\');
}

/**
 * Return true if the root anchor the shellext is operating on belongs to an
 * @aerofs.com user
 */
bool AeroFSShellExtension::shouldEnableTestingFeatures() const
{
	return str_ends_with(m_userId, L"@aerofs.com");
}

/**
 * Compute path flags for a given path to adapt context menu
 * The flags are an OR combination of values defined in the PathFlag enum
 */
int AeroFSShellExtension::pathFlags(const std::wstring& path) const
{
	int flags = 0;
	if (lowercase(path) == m_rootAnchor) {
		flags |= RootAnchor;
	}
	std::wstring long_path = std::wstring(L"\\\\?\\") + path;
	DWORD attributes = GetFileAttributes(long_path.c_str());
	if (attributes != INVALID_FILE_ATTRIBUTES) {
		if (attributes & FILE_ATTRIBUTE_DIRECTORY) {
			flags |= Directory;
		} else {
			flags |= File;
		}
	}
	return flags;
}

/**
Called by AeroSocket to react on a message sent by the GUI
*/
void AeroFSShellExtension::parseNotification(const ShellextNotification& notification)
{
	switch (notification.type()) {
		case ShellextNotification_Type_PATH_STATUS:
			onPathStatusNotification(notification.path_status());
			break;

		case ShellextNotification_Type_ROOT_ANCHOR:
			setRootAnchor(notification.root_anchor().path());
			if (notification.root_anchor().has_user()) {
				setUserId(notification.root_anchor().user());
			}
			break;

		case ShellextNotification_Type_CLEAR_STATUS_CACHE:
			clearCache();
			break;

		case ShellextNotification_Type_LINK_SHARING_ENABLED:
			m_is_link_sharing_enabled = notification.link_sharing_enabled().is_link_sharing_enabled();
			break;

		default:
			break;
	}
}

bool AeroFSShellExtension::isLinkSharingEnabled()
{
	return m_is_link_sharing_enabled;
}

static int overlayForStatus(const PBPathStatus& status) {
	// temporary downloading takes precedence over potentially long-lasting states
	if (status.flags() & PBPathStatus_Flag_DOWNLOADING)	return O_Downloading;
	// conflict state takes precedence over sync status
	if (status.flags() & PBPathStatus_Flag_CONFLICT)	return O_Conflict;
	// in-sync takes precedence over uploading
	if (status.sync() == PBPathStatus_Sync_IN_SYNC)     return O_InSync;
	// uploading takes precedence over out-of-sync
	if (status.flags() & PBPathStatus_Flag_UPLOADING)	return O_Uploading;
	if (status.sync() == PBPathStatus_Sync_OUT_SYNC)    return O_OutSync;
	return O_None;
}

/**
 * Check the sync status cache and query the GUI on cache miss
 *
 * The caller is responsible for checking that \a path is under the root anchor
 */
Overlay AeroFSShellExtension::overlay(const std::wstring& path)
{
	if (!isUnderRootAnchor(path)) return O_None;

	// TODO: use RTROOT-relative path as cache key to save memory
	const std::wstring key = lowercase(path);
	int status = m_cache->value(key, -1);

	if (status == -1) {
		// put placeholder in cache to prevent repeated calls to GUI
		m_cache->insert(key, O_None);

		// query GUI for sync status
		ShellextCall call;
		call.set_type(ShellextCall_Type_GET_PATH_STATUS);
		call.mutable_get_path_status()->set_path(narrow(path));
		m_socket->sendMessage(call);

		status = O_None;
	} else {
		// TODO: clear placeholders (O_None) after a cooldown period?
	}

	return (Overlay)status;
}

 void AeroFSShellExtension::evicted(const std::wstring& key, int value) const
 {
	 refreshShellItem(key, false);
 }


/**
 * Process notifications from the GUI about file status changes
 */
void AeroFSShellExtension::onPathStatusNotification(const PathStatusNotification& fstatus)
{
	if (fstatus.path().empty()) {
		ERROR_LOG("Received an empty status notification");
		return;
	}

	PBPathStatus status = fstatus.status();
	const std::wstring path = lowercase(widen(fstatus.path()));

	if (!isUnderRootAnchor(path)) {
		INFO_LOG("Received a status update for path outside root anchor");
		return;
	}

	// TODO: use RTROOT-relative path as cache key to save memory
	int oldOverlay = m_cache->value(path, -1);
	int newOverlay = overlayForStatus(status);

	DEBUG_LOG("Received status update for " << path << " "
		<< status.sync() << ":" << status.flags()
		<< "[ " << oldOverlay << " -> " << newOverlay << "]");

	if (oldOverlay != -1 && newOverlay != oldOverlay) {
		// update cache
		m_cache->insert(path, newOverlay);
		// mark overlay as dirty
		refreshShellItem(path, false);
	}
}

/**
Called by the GUI to set where the root anchor is.
*/
void AeroFSShellExtension::setRootAnchor(const std::string& path)
{
	std::wstring p = lowercase(widen(path));

	assert(p.length() > 0 && p.length() < MAX_PATH);

	// Do not accept a root anchor with a trailing slash. This is part of the spec in shellext.proto
	if (p.at(p.length() - 1) == '\\') {
		ERROR_LOG("Root anchor has trailing slash " << p);
		return;
	}

	m_rootAnchor = p;

	INFO_LOG("Initialized with root anchor: " << m_rootAnchor);
	clearCache();
}

void AeroFSShellExtension::setUserId(const std::string& user)
{
	m_userId = widen(user);
}

/**
Clear the file status cache - all icon overlays are reset.
*/
void AeroFSShellExtension::clearCache()
{
	// clear cache
	m_cache->clear();
	refreshShellItem(m_rootAnchor, true);
}

/**
Tell the shell to refresh the icon overlay a single item (file or folder)

If recursive is false, only the icon for the item pointed by path will be refreshed.
If recursive is true, all icons under the item pointed by path, including the item itself,
will be refreshed.
*/
void AeroFSShellExtension::refreshShellItem(const std::wstring& path, bool recursive) const
{
	LONG action = recursive ? SHCNE_UPDATEDIR : SHCNE_UPDATEITEM;
	SHChangeNotify(action, SHCNF_PATH | SHCNF_FLUSHNOWAIT, path.c_str(), NULL);
}

void AeroFSShellExtension::showSyncStatusDialog(const std::wstring& path)
{
	ShellextCall call;
	call.set_type(ShellextCall_Type_SYNC_STATUS);
	call.mutable_sync_status()->set_path(narrow(path.c_str()));
	m_socket->sendMessage(call);
	INFO_LOG("SyncStatusDialog shown");
}

void AeroFSShellExtension::showVersionHistoryDialog(const std::wstring& path)
{
	ShellextCall call;
	call.set_type(ShellextCall_Type_VERSION_HISTORY);
	call.mutable_version_history()->set_path(narrow(path.c_str()));
	m_socket->sendMessage(call);
	INFO_LOG("VersionHistoryDialog shown");
}

void AeroFSShellExtension::createLink(const std::wstring& path)
{
	ShellextCall call;
	call.set_type(ShellextCall_Type_CREATE_LINK);
	call.mutable_create_link()->set_path(narrow(path.c_str()));

	m_socket->sendMessage(call);
	INFO_LOG("Create Link");
}

void AeroFSShellExtension::showShareFolderDialog(const std::wstring& path)
{
	ShellextCall call;
	call.set_type(ShellextCall_Type_SHARE_FOLDER);
	call.mutable_share_folder()->set_path(narrow(path.c_str()));
	m_socket->sendMessage(call);
	INFO_LOG("ShareFolderDialog shown");
}

void AeroFSShellExtension::showConflictResolutionDialog(const std::wstring& path)
{
	ShellextCall call;
	call.set_type(ShellextCall_Type_CONFLICT_RESOLUTION);
	call.mutable_conflict_resolution()->set_path(narrow(path.c_str()));
	m_socket->sendMessage(call);
	INFO_LOG("ConflictResolutionDialog shown");
}

void AeroFSShellExtension::sendGreeting()
{
	ShellextCall call;
	call.set_type(ShellextCall_Type_GREETING);
	call.mutable_greeting()->set_protocol_version(PROTOCOL_VERSION);
	m_socket->sendMessage(call);
	INFO_LOG("Initial greeting sent");
}

/**
Get the GUI server socket file name.
*/
wstring AeroFSShellExtension::getSocketFileName()
{
	tstring file_name;
	DWORD username_len = UNLEN + 1;
	TCHAR username[UNLEN+1] = {_T('\0')};

	if (!GetUserName(username, &username_len)) {
		// Shouldn't ever get here.
		ERROR_LOG("GetUserName failed. Err code: " << GetLastError());
		exit(1);
	}
	file_name.append(PIPE_NAMESPACE);
	file_name.append(username, username_len - 1);
	file_name.append(SOCKET_FILE_NAME_SUFFIX);
	return file_name;
}

//////////////////////////////////////////
// DLL EXPORTED FUNCTIONS
/////////////////////////////////////////

// DLL Entry Point
extern "C" BOOL WINAPI DllMain(HINSTANCE hInstance, DWORD dwReason, LPVOID lpReserved)
{
	return _instance.DllMain(dwReason, lpReserved);
}

STDAPI DllCanUnloadNow(void)
{
	HRESULT hr = _instance.DllCanUnloadNow();
	if (hr == S_OK) {
		_instance.cleanup();
	}
	return hr;
}

// Returns a class factory to create an object of the requested CLSID
STDAPI DllGetClassObject(REFCLSID rclsid, REFIID riid, LPVOID* ppv)
{
	return _instance.DllGetClassObject(rclsid, riid, ppv);
}

// Adds entries to the system registry.
STDAPI DllRegisterServer(void)
{
	DEBUG_LOG(" ========== REG SERVER ===========");
	HRESULT hr = _instance.DllRegisterServer(FALSE);
	SHChangeNotify(SHCNE_ASSOCCHANGED, SHCNF_IDLIST, NULL, NULL);
	return hr;
}

// Removes entries from the system registry.
STDAPI DllUnregisterServer(void)
{
	DEBUG_LOG(" ========== UNREG SERVER ===========");
	HRESULT hr = _instance.DllUnregisterServer(FALSE);
	SHChangeNotify(SHCNE_ASSOCCHANGED, SHCNF_IDLIST, NULL, NULL);
	// Force the shell extension module to be unloaded to allow a different
	// (presumably newer) version to be loaded instead. Without this call the
	// old version will be kept in memory by Explorer until reboot.
	// NB: this will only take effect after all explorer windows are closed
	CoFreeUnusedLibrariesEx(200, 0);
	return hr;
}

// Adds/Removes entries to the system registry per user per machine.
STDAPI DllInstall(BOOL install, LPCWSTR cmdLine)
{
	HRESULT hr = E_FAIL;

	if (cmdLine) {
		if (std::wstring(cmdLine) == L"user") {
			ATL::AtlSetPerUserRegistration(true);
		}
	}

	if (install) {
		hr = DllRegisterServer();
		if (FAILED(hr)) {
			DllUnregisterServer();
		}
	} else {
		hr = DllUnregisterServer();
	}

	return hr;
}
