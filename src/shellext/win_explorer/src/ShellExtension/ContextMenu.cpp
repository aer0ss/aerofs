#include "stdafx.h"
#include "ContextMenu.h"
#include "logger.h"
#include "AeroFSShellExtension.h"
#include "shobjidl.h"

#define AEROFS_MENUITEM_MAGIC 0xAAEE00FF  // Used to tell if we already inserted the AeroFS item in a context menu,
                                          // because in certain circumstances, the Shell can call us twice on the same context menu.
                                          // Why? Because we register our extension to handle several types of shell objects,
                                          // but some objects can belong to more than one type. We would get called once for each type we registered.

// Initialization code should go here
HRESULT ContextMenu::FinalConstruct()
{
	m_path = L"";
	m_instance = static_cast<AeroFSShellExtension*>(ATL::_pAtlModule);
	m_menuIcon = createMenuItemBitmap(IDI_LOGO);
	return S_OK;
}

// De-initialization code should go here
void ContextMenu::FinalRelease()
{
	DeleteObject(m_menuIcon);
}

/**
Called by the Shell with the path of the object(s) the user right-clicked on.
Return S_OK to tell the Shell that we want to modify this menu, and E_INVALIDARG otherwise.
*/
HRESULT ContextMenu::Initialize(PCIDLIST_ABSOLUTE pidlFolder, IDataObject* pdtobj, HKEY hkeyProgID)
{
	// Do not show the AeroFS menu if we are not connected to the GUI
	if (!m_instance->isConnectedToGUI()) {
		return E_INVALIDARG;
	}

	m_path = pdtobj ?  getPathFromDataObject(pdtobj) : getPathFromPidl(pidlFolder);
	if (m_path.empty()) {
		return E_INVALIDARG;
	}

	if (m_instance->isUnderRootAnchor(m_path)) {
		return S_OK;
	}

	return E_INVALIDARG;
}

/**
Return the first path in a IDataObject structure.
The Shell fills this structure with the paths of the files that are selected during a right-click operation.
The first path in the structure is the one the user right-clicked on.
In case of error, returns an empty string.
*/
std::wstring ContextMenu::getPathFromDataObject(IDataObject* pdtobj) const
{
	FORMATETC etc = { CF_HDROP, NULL, DVASPECT_CONTENT, -1, TYMED_HGLOBAL };
	STGMEDIUM stg = { TYMED_HGLOBAL };

	pdtobj->GetData(&etc, &stg);
	HDROP hdrop = (HDROP) GlobalLock(stg.hGlobal);

	// MSDN on DragQueryFile:
	// If the index value is between zero and the total number of dropped
	// files, and the lpszFile buffer address is NULL, the return value is the
	// required size, in characters, of the buffer, not including the
	// terminating null character.
	int needed = DragQueryFile(hdrop, 0, 0, 0) + 1;
	std::wstring path = L"";
	wchar_t* buf = new wchar_t[needed];
	if (buf) {
		int success = DragQueryFile(hdrop, 0, buf, needed);
		path = success ? std::wstring(buf) : L"";
		delete [] buf;
	}

	GlobalUnlock (stg.hGlobal);
	ReleaseStgMedium (&stg);

	return path;
}

/**
Return the first path from a ITEMLISTID structure
This is used when the user right-click inside a folder (as opposed to right-clicking on a file)
Returns: the path of the folder the user right-clicked on, or an empty string in case of error
*/
std::wstring ContextMenu::getPathFromPidl(PCIDLIST_ABSOLUTE pidlFolder) const
{
	// MAX_PATH here is okay, and our only choice if we support WinXP
	// SHGetPathFromIDListEx() was added in Vista
	wchar_t buf[MAX_PATH];
	BOOL success = SHGetPathFromIDList(pidlFolder, buf);
	return success ? std::wstring(buf) : L"";
	/*
	 * N.B. (DF): if we drop support for Windows XP, use:
	int bufSize = 65535;
	wchar_t* buf = new wchar_t[bufSize + 1];
	if (!buf) return L"";
	BOOL success = SHGetPathFromIDListEx(pidlFolder, buf, bufSize, 0);
	std::wstring path = success ? std::wstring(buf) : L"";
	delete [] buf;
	return path;
	*/
}

/**
Called by the Shell with a context menu that we can modify.
See the MSDN docs for this method for more info about the return value - it's important to get it right.
*/
HRESULT ContextMenu::QueryContextMenu(HMENU hmenu, UINT position, UINT idCmdFirst, UINT idCmdLast, UINT flags)
{
	// If the flags include CMF_DEFAULTONLY we shouldn't do anything.
	if (flags & CMF_DEFAULTONLY) {
		return MAKE_HRESULT(SEVERITY_SUCCESS, FACILITY_NULL, 0);
	}

	// Check if we already added our menu entry. We need to do this because the shell may call us several times.
	// So we iterate through all menu entries and check if the dwItemData member is set to a magic number.
	// This magic number is set to our menu item when we insert it.
	int count = GetMenuItemCount(hmenu);
	for (int i = 0; i < count; ++i) {
		MENUITEMINFO miif = {};
		miif.cbSize = sizeof(MENUITEMINFO);
		miif.fMask = MIIM_DATA;
		GetMenuItemInfo(hmenu, i, TRUE, &miif);
		if (miif.dwItemData == AEROFS_MENUITEM_MAGIC) {
			return MAKE_HRESULT(SEVERITY_SUCCESS, FACILITY_NULL, 0);
		}
	}

	int entryCount = 0;
	int pflags = m_instance->pathFlags(m_path);

	HMENU submenu = CreatePopupMenu();

	if (!(pflags & Directory) && m_instance->overlay(m_path) == O_Conflict) {
		AppendMenu(submenu, MF_STRING, idCmdFirst + ConflictResolutionMenuId, L"Resolve Conflict...");
		++entryCount;
	}

	if (m_instance->shouldEnableTestingFeatures()) {
		AppendMenu(submenu, MF_STRING, idCmdFirst + SyncStatusMenuId, L"Sync Status...");
		++entryCount;
	}

	AppendMenu(submenu, MF_STRING, idCmdFirst + VersionHistoryMenuId, L"Sync History...");
	++entryCount;

	if (((pflags & (Directory | File)) && !(pflags & RootAnchor))
			&& m_instance->isLinkSharingEnabled()) {
		AppendMenu(submenu, MF_STRING, idCmdFirst + CreateLinkMenuId, L"Create Link");
		++entryCount;
	}

	if ((pflags & Directory) && !(pflags & RootAnchor)) {
		AppendMenu(submenu, MF_STRING, idCmdFirst + ShareFolderMenuId, L"Share This Folder...");
		++entryCount;
	}

	if (entryCount <= 0) {
		DestroyMenu(submenu);
		return MAKE_HRESULT(SEVERITY_SUCCESS, FACILITY_NULL, 0);
	}

	MENUITEMINFO mSep = {};
	mSep.cbSize = sizeof(MENUITEMINFO);
	mSep.fMask = MIIM_TYPE;
	mSep.fType = MFT_SEPARATOR;

	MENUITEMINFO mii = {};
	mii.cbSize = sizeof(MENUITEMINFO);
	mii.fMask = MIIM_SUBMENU | MIIM_STRING | MIIM_ID | MIIM_CHECKMARKS | MIIM_DATA;
	mii.wID = idCmdFirst + AeroFSMenuId;
	mii.hSubMenu = submenu;
	mii.dwTypeData = L"AeroFS";
	mii.hbmpChecked = m_menuIcon;
	mii.hbmpUnchecked = m_menuIcon;
	mii.dwItemData = AEROFS_MENUITEM_MAGIC; // Set our magic number si that we can tell if we already inserted the AeroFS menu

	InsertMenuItem(hmenu, position++, TRUE, &mSep);
	InsertMenuItem(hmenu, position++, TRUE, &mii);
	InsertMenuItem(hmenu, position++, TRUE, &mSep);

	// The return value should be idCmdFirst + (largest id used) + 1
	// Because AeroFSMenuId is the last element of the enum and we always use it
	// the largest id that we use is always (idCmdFirst + AeroFSMenuId)
	return MAKE_HRESULT(SEVERITY_SUCCESS, FACILITY_NULL, AeroFSMenuId + 1);
}

HRESULT ContextMenu::InvokeCommand(LPCMINVOKECOMMANDINFO pInfo)
{
	// Make sure that lpVerb doesn't point to a string
	if (HIWORD(pInfo->lpVerb) != 0) {
		return E_INVALIDARG;
	}

	switch ((MenuId) LOWORD(pInfo->lpVerb)) {
	case ConflictResolutionMenuId:
		m_instance->showConflictResolutionDialog(m_path);
		return S_OK;
	case SyncStatusMenuId:
		m_instance->showSyncStatusDialog(m_path);
		return S_OK;
	case VersionHistoryMenuId:
		m_instance->showVersionHistoryDialog(m_path);
		return S_OK;
	case CreateLinkMenuId:
		m_instance->createLink(m_path);
		return S_OK;
	case ShareFolderMenuId:
		m_instance->showShareFolderDialog(m_path);
		return S_OK;

	default:
		return E_INVALIDARG;
	}
}

/**
Called by the Shell to get the string displayed in Explorer's status bar.
Useless for Windows > XP
*/
HRESULT ContextMenu::GetCommandString(UINT_PTR idCmd, UINT uFlags, UINT *pwReserved, LPSTR pszName, UINT cchMax)
{
	return E_INVALIDARG;
}

/**
Creates a bitmap suitable for use in the hbmpChecked / hbmpUnchecked fields of a MENUITEMINFO structure
The bitmap will have the size of SM_CXMENUCHECK x SM_CYMENUCHECK, and its background color will be set
to the system color for menu backgrounds.

See http://www.nanoant.com/programming/themed-menus-icons-a-complete-vista-xp-solution for a discussion on
how to properly display context menu icons. We use a simple method that has the drawback of requiring a
non-standard (and slightly smaller) icon size - but it's way simpler than any other method.

@param iconId: the id of an icon resource to create the bitmap from
@note: you must destroy the bitmap when it's no longer needed by calling DeleteObject
*/
HBITMAP ContextMenu::createMenuItemBitmap(int iconId)
{
	int width = GetSystemMetrics(SM_CXMENUCHECK);
	int height = GetSystemMetrics(SM_CYMENUCHECK);

	// Create a DC to draw on
	HWND desktop = GetDesktopWindow();
	HDC screenDC = GetDC(desktop);
	HDC destDC = CreateCompatibleDC(screenDC);

	// Create a new bitmap of icon size and select it into the DC
	HBITMAP bmp = CreateCompatibleBitmap(screenDC, width, height);
	HBITMAP prevBmp = (HBITMAP) SelectObject(destDC, bmp);

	// Fill the background with the menu background color
	RECT rect = {0, 0, width, height};
	SetBkColor(destDC, GetSysColor(COLOR_MENU));
	ExtTextOut(destDC, 0, 0, ETO_OPAQUE, &rect, NULL, 0, NULL);

	// Draw the icon into the DC
	HICON hIcon = (HICON) LoadImage(_AtlBaseModule.GetModuleInstance(), MAKEINTRESOURCE(iconId), IMAGE_ICON, width, height, LR_DEFAULTCOLOR);
	DrawIconEx(destDC, 0, 0, hIcon, width, height, 0, NULL, DI_NORMAL);

	// Cleanup
	SelectObject(destDC, prevBmp);
	DeleteDC(destDC);
	ReleaseDC(desktop, screenDC);
	DestroyIcon(hIcon);
	return bmp;
}
