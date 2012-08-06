#pragma once

#include "shlobj.h"
#include "resource.h"
#include "rgs_helper.h"
#include "AeroNode.h"

using namespace ATL;

class AeroFSShellExtension;

/**
Since a shell overlay handler can only display one single icon overlay,
we need separate handlers for each icon we want to display (ie: downloading,
uploading, etc...).

To achieve that, we put all our code in the template base class "OverlayBase",
and derive concrete classes DownloadOverlay, UploadOverlay, etc..., for each
type of overlay we want to handle.

Each of the derived classes simply defines it's own CLSID, as well as the icon
it wants to display and which AeroNode::Status it should be associated with.
*/

template <class T>
class __declspec(novtable) OverlayBase :
	public CComObjectRootEx<CComSingleThreadModel>,
	public CComCoClass<T, &__uuidof(T)>,
	public IShellIconOverlayIdentifier
{
	DECLARE_NOT_AGGREGATABLE(T)
	DECLARE_PROTECT_FINAL_CONSTRUCT()

	DECLARE_REGISTRY_RESOURCEID_WITH_ADMIN(IDR_OVERLAYICONS_RGS, IDR_OVERLAYICONS_ADMIN_RGS)
	BEGIN_REGISTRY_MAP(T)
		REGMAP_ENTRY("DESCRIPTION", "AeroFS Shell Extension Class")
		REGMAP_FUNCTION("NAME",     T::name)
		REGMAP_UUID ("CLSID",       _uuidof(T))
	END_REGISTRY_MAP()

	BEGIN_COM_MAP(T)
		COM_INTERFACE_ENTRY(IShellIconOverlayIdentifier)
	END_COM_MAP()

	// CComObjectRootEx
	HRESULT FinalConstruct();
	void FinalRelease();

	//IShellIconOverlayIdentifier interface
	HRESULT STDMETHODCALLTYPE GetOverlayInfo(PWSTR pwszIconFile, int cchMax, int* pIndex, DWORD* pdwFlags);
	HRESULT STDMETHODCALLTYPE GetPriority(int* pPriority);
	HRESULT STDMETHODCALLTYPE IsMemberOf(PCWSTR pwszPath, DWORD dwAttrib);

protected:
	virtual AeroNode::Status overlayForStatus() const = 0;
	virtual int overlayIcon() const = 0;

private:
	AeroFSShellExtension* m_instance;
};

class
	__declspec(uuid("882108B1-26E6-4926-BC70-EA1D738D5DEB"))
	__declspec(novtable)
DownloadOverlay : public OverlayBase<DownloadOverlay>
{
public:
	static wchar_t* name();
	AeroNode::Status overlayForStatus() const;
	int overlayIcon() const;
};

class
	__declspec(uuid("882108B2-26E6-4926-BC70-EA1D738D5DEB"))
	__declspec(novtable)
UploadOverlay : public OverlayBase<UploadOverlay>
{
public:
	static wchar_t* name();
	AeroNode::Status overlayForStatus() const;
	int overlayIcon() const;
};

OBJECT_ENTRY_AUTO(__uuidof(DownloadOverlay), DownloadOverlay)
OBJECT_ENTRY_AUTO(__uuidof(UploadOverlay), UploadOverlay)
