/**
  ContextMenu

  This is the COM class that handles the right-click menu.

  Instances of this class are created and destroyed by the Shell every time the user right-clicks
  on a shell object that we potentially want to handle.
*/

#pragma once

#include "shobjidl.h"
#include "shlobj.h"
#include "resource.h"
#include "rgs_helper.h"
#include <string>

using namespace ATL;

class AeroFSShellExtension;

class
#if defined(_M_IX86)
	__declspec(uuid("882108B0-26E6-4926-BC70-EA1D738D5DEB"))
#elif defined(_M_AMD64)
	__declspec(uuid("882108B8-26E6-4926-BC70-EA1D738D5DEB"))
#else
#error Unexpected build architecture.
#endif
	__declspec(novtable)                                         // ATL_NO_VTABLE
ContextMenu :
	public CComObjectRootEx<CComSingleThreadModel>,
	public CComCoClass<ContextMenu, &__uuidof(ContextMenu)>,
	public IShellExtInit,
	public IContextMenu
{
public:
	DECLARE_NOT_AGGREGATABLE(ContextMenu)
	DECLARE_PROTECT_FINAL_CONSTRUCT()

	// Sets the variables that are going to be replaced in ContextMenu.rgs
	DECLARE_REGISTRY_RESOURCEID_WITH_ADMIN(IDR_CONTEXTMENU_RGS, IDR_CONTEXTMENU_ADMIN_RGS)
	BEGIN_REGISTRY_MAP(ContextMenu)
		REGMAP_ENTRY("DESCRIPTION", "AeroFS Shell Extension Class")
		REGMAP_ENTRY("NAME",        "AeroFSShellExtension")
		REGMAP_UUID ("CLSID",       _uuidof(ContextMenu))
	END_REGISTRY_MAP()

	// Tells ATL what COM interfaces we implement
	BEGIN_COM_MAP(ContextMenu)
		COM_INTERFACE_ENTRY(IShellExtInit)
		COM_INTERFACE_ENTRY(IContextMenu)
	END_COM_MAP()

	ContextMenu() {}

	// CComObjectRootEx interface
	HRESULT FinalConstruct();
	void FinalRelease();

	// IShellExtInit interface
	HRESULT STDMETHODCALLTYPE Initialize(PCIDLIST_ABSOLUTE pidlFolder, IDataObject *pdtobj, HKEY hkeyProgID);

	//IContextMenu interface
	HRESULT STDMETHODCALLTYPE QueryContextMenu(HMENU hmenu, UINT uMenuIndex, UINT uidFirstCmd, UINT uidLastCmd, UINT uFlags);
	HRESULT STDMETHODCALLTYPE InvokeCommand(LPCMINVOKECOMMANDINFO pCmdInfo);
	HRESULT STDMETHODCALLTYPE GetCommandString(UINT_PTR idCmd, UINT uFlags, UINT *pwReserved, LPSTR pszName, UINT cchMax);

private:
	HBITMAP createMenuItemBitmap(int iconId);
	std::wstring getPathFromDataObject(IDataObject*) const;
	std::wstring getPathFromPidl(PCIDLIST_ABSOLUTE) const;

	typedef enum {
		SyncStatusMenuId,
		VersionHistoryMenuId,
		ShareFolderMenuId,
		ConflictResolutionMenuId,
		CreateLinkMenuId,
		AeroFSMenuId // this should always be the last item of this enum. (see comment in QueryContextMenu)
	} MenuId;

	AeroFSShellExtension* m_instance;

	std::wstring m_path;
	HBITMAP m_menuIcon;
};

OBJECT_ENTRY_AUTO(__uuidof(ContextMenu), ContextMenu)
