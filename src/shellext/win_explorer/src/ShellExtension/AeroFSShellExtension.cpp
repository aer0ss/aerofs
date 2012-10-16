﻿#include "stdafx.h"
#include "AeroFSShellExtension.h"

#include <string>
#include <regex>
#include <fstream>
#include <shlobj.h>

#include "AeroSocket.h"
#include "OverlayCache.h"
#include "logger.h"
#include "string_helpers.h"
#include "../../gen/shellext.pb.h"

#define GUIPORT_DEFAULT 50195
#define GUIPORT_OFFSET 2
#define DELAY_BEFORE_CONNECTION_RETRY 2000 // milliseconds
#define PROTOCOL_VERSION 4
#define OVERLAY_CACHE_SIZE_LIMIT 10000 // max number of path for which overlay state is cached

AeroFSShellExtension _instance;

AeroFSShellExtension::AeroFSShellExtension()
	: m_socket(new AeroSocket()),
	m_cache(new OverlayCache(OVERLAY_CACHE_SIZE_LIMIT)),
	m_rootAnchor(),
	m_lastConnectionAttempt(0),
	m_port(0),
	m_syncStatCached(false)
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
	m_socket->forceDisconnect();
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
	if (!m_port) {
		m_port = readPortNumber();
	}

	INFO_LOG("Trying to connect on port " << m_port);
	m_lastConnectionAttempt = GetTickCount();
	m_socket->connect(m_port);
}

/**
 * Returns true if and only if: path is under root anchor
 * Note: the test is inclusive (i.e returns true for root anchor)
 */
bool AeroFSShellExtension::isUnderRootAnchor(const std::wstring& path)
{
	if (path.empty() || path.length() >= MAX_PATH
			|| m_rootAnchor.empty() || m_rootAnchor.length() >= MAX_PATH
			|| path.length() < m_rootAnchor.length()) {
		return false;
	}

	// Check that path starts with root anchor
	return str_starts_with(lowercase(path), m_rootAnchor) &&
		(path.length() == m_rootAnchor.length() || path.at(m_rootAnchor.length()) == '\\');
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
	if (PathIsDirectory(path.c_str())) {
		flags |= Directory;
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

		default:
			break;
	}
}

static int overlayForStatus(const PBPathStatus& status) {
	if (status.flags() & PBPathStatus_Flag_DOWNLOADING)	return O_Downloading;
	if (status.flags() & PBPathStatus_Flag_UPLOADING)	return O_Uploading;
	switch (status.sync()) {
		case PBPathStatus_Sync_OUT_SYNC:				return O_OutSync;
		case PBPathStatus_Sync_PARTIAL_SYNC:			return O_PartialSync;
		case PBPathStatus_Sync_IN_SYNC:					return O_InSync;
		default: break;
	}
	return O_None;
}

/**
 * Check the sync status cache and query the GUI on cache miss
 *
 * The caller is responsible for checking that \a path is under the root anchor
 */
Overlay AeroFSShellExtension::overlay(std::wstring& path)
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
		call.mutable_get_path_status()->set_path(to_string(path));

		m_socket->sendMessage(call);

		status = O_None;
	}

	if (!shouldEnableTestingFeatures()) {
		if (status != O_Downloading && status != O_Uploading) return O_None;
	}

	return (Overlay)status;
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
	const std::wstring path = lowercase(to_wstring(fstatus.path()));

	if (!isUnderRootAnchor(path)) {
		INFO_LOG("Received a status update for path outside root anchor");
		return;
	}

	INFO_LOG("Received status update for " << path << " "
		<< status.sync() << ":" << status.flags());

	// clear cache if sync status is no longer reliable
	if (status.sync() == PBPathStatus_Sync_UNKNOWN) {
		if (m_syncStatCached) clearCache();
	} else {
		m_syncStatCached = true;
	}

	// TODO: use RTROOT-relative path as cache key to save memory
	int oldOverlay = m_cache->value(path, -1);
	int newOverlay = overlayForStatus(status);
	if (oldOverlay != newOverlay) {
		// update cache
		m_cache->insert(path, newOverlay);
		// mark overlay as dirty
		SHChangeNotify(SHCNE_UPDATEITEM, SHCNF_PATH | SHCNF_FLUSHNOWAIT, path.c_str(), NULL);
	}
}

/**
Called by the GUI to set where the root anchor is.
*/
void AeroFSShellExtension::setRootAnchor(const std::string& path)
{
	std::wstring p = lowercase(to_wstring(path));

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
	m_userId = to_wstring(user);
}

/**
Clear the file status cache - all icon overlays are reset.
*/
void AeroFSShellExtension::clearCache()
{
	// clear cache
	m_cache->clear();
	m_syncStatCached = false;

	// Tell the shell to refresh the icons
	SHChangeNotify(SHCNE_ASSOCCHANGED, SHCNF_IDLIST, NULL, NULL);
}

void AeroFSShellExtension::showSyncStatusDialog(const std::wstring& path)
{
	ShellextCall call;
	call.set_type(ShellextCall_Type_SYNC_STATUS);
	call.mutable_sync_status()->set_path(to_string(path.c_str()));

	m_socket->sendMessage(call);
}

void AeroFSShellExtension::showVersionHistoryDialog(const std::wstring& path)
{
	ShellextCall call;
	call.set_type(ShellextCall_Type_VERSION_HISTORY);
	call.mutable_version_history()->set_path(to_string(path.c_str()));

	m_socket->sendMessage(call);
}

void AeroFSShellExtension::showShareFolderDialog(const std::wstring& path)
{
	ShellextCall call;
	call.set_type(ShellextCall_Type_SHARE_FOLDER);
	call.mutable_share_folder()->set_path(to_string(path.c_str()));

	m_socket->sendMessage(call);
}

void AeroFSShellExtension::sendGreeting()
{
	ShellextCall call;
	call.set_type(ShellextCall_Type_GREETING);
	call.mutable_greeting()->set_protocol_version(PROTOCOL_VERSION);

	m_socket->sendMessage(call);
}

/**
Reads the GUI server port from C:\Users\[username]\AppData\Roaming\AeroFS\pb
In case of error, returns GUIPORT_DEFAULT
*/
unsigned short AeroFSShellExtension::readPortNumber()
{
	// Get the path to C:\Users\[username]\AppData\Roaming

	wchar_t buf[MAX_PATH];
	SHGetFolderPath(NULL, CSIDL_APPDATA, NULL, SHGFP_TYPE_CURRENT, buf);
	std::wstring path(buf);

	if (path.empty()) {
		ERROR_LOG("SHGetFolderPath failed. Using default port");
		return GUIPORT_DEFAULT;
	}

	// Open the pb file

	path += L"\\AeroFS\\pb";
	HANDLE hFile = CreateFile(path.c_str(), GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    if (hFile == INVALID_HANDLE_VALUE) {
		ERROR_LOG("Could not open " << path << ". Using default port");
		return GUIPORT_DEFAULT;
    }

	// Read the port number

	char strPort[10] = {0};
	DWORD bytesRead = 0;
	ReadFile(hFile, strPort, sizeof(strPort) - 1, &bytesRead, NULL);
	CloseHandle(hFile);

	int port = atoi(strPort);

	if (port <= 0 || port > 65535 - GUIPORT_OFFSET) {
		ERROR_LOG("Invalid port number. Using default port");
		return GUIPORT_DEFAULT;
	}

	return port + GUIPORT_OFFSET;
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
	// Force the sheel extension module to be unloaded to allow a different
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


