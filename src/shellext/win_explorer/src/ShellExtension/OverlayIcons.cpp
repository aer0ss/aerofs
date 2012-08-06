#include "stdafx.h"
#include "OverlayIcons.h"
#include "logger.h"
#include "AeroFSShellExtension.h"

template <class T>
HRESULT OverlayBase<T>::FinalConstruct()
{
	m_instance = static_cast<AeroFSShellExtension*>(ATL::_pAtlModule);
	return S_OK;
}

template <class T>
void OverlayBase<T>::FinalRelease()
{
}

/**
Called by the shell during initialization to get the file name and the index of the icon to display
*/
template <class T>
HRESULT OverlayBase<T>::GetOverlayInfo(PWSTR iconFileName, int maxChars, int* pIndex, DWORD* pFlags)
{
	GetModuleFileName(_AtlBaseModule.m_hInstResource, iconFileName, maxChars);
	*pIndex = -overlayIcon();  // A negative value tells the shell that we are passing a resource ID instead of an icon index
	*pFlags = ISIOI_ICONFILE | ISIOI_ICONINDEX;
	return S_OK;
}

/**
Supposedly used to determine which overlay gets displayed, if there are more than one overlay for a file.
Actually pointless since everybody just returns the highest priority anyway. (And the windows documentation encourages that.)
*/
template <class T>
HRESULT OverlayBase<T>::GetPriority(int* pPriority)
{
	*pPriority = 0;
	return S_OK;
}

/**
Called by the shell for any file whose icon is about to be drawn.
Return S_OK if the overlay should be displayed, S_FALSE otherwise.
*/
template <class T>
HRESULT OverlayBase<T>::IsMemberOf(PCWSTR pwszPath, DWORD dwAttrib)
{
	if (!m_instance || !m_instance->isConnectedToGUI()) {
		return S_FALSE;
	}

	AeroNode* rootNode = m_instance->rootNode();
	if (!rootNode) {
		return S_FALSE;
	}

	AeroNode* node = rootNode->nodeAtPath(std::wstring(pwszPath), false);
	if (node && node->status() == overlayForStatus()) {
		return S_OK;
	}

	return S_FALSE;
}

/// Download Overlay ///

wchar_t * DownloadOverlay::name()
{
	// Prepend the number at the begining of the name instead to the end to increase our chances of ending up
	// in the restricted list of 15 overlay handlers that are available on the system.
	// (TortoiseOverlay does the same - Dropbox does not :)
	return L"1_AeroFSShellExtension";
}

AeroNode::Status DownloadOverlay::overlayForStatus() const
{
	return AeroNode::Downloading;
}

int DownloadOverlay::overlayIcon() const
{
	return IDI_DOWNLOAD_OVERLAY;
}

/// Upload Overlay ///

wchar_t * UploadOverlay::name()
{
	return L"2_AeroFSShellExtension";
}

AeroNode::Status UploadOverlay::overlayForStatus() const
{
	return AeroNode::Uploading;
}

int UploadOverlay::overlayIcon() const
{
	return IDI_UPLOAD_OVERLAY;
}
