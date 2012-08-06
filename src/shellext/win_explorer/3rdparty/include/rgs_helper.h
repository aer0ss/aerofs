// Copyright 2011 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// Defines a map for adding variables to rgs files. This allows COM object
// classes to declare the values of these variables so that we don't need to
// copy/paste them and manually keep them in sync.
// To use this, declare the registry ID of your RGS file using
// the DECLARE_REGISTRY_RESOURCEID_EX macro, instead of the
// DECLARE_REGISTRY_RESOURCEID, then add a registry map to your class
// using the registry map macros:
//  BEGIN_REGISTRY_MAP(MyClassName)
//    REGMAP_ENTRY("NAME", "MyClassName Class")
//    REGMAP_UUID("CLSID", CLSID_MyClassName)
//  END_REGISTRY_MAP()
//
// You can then refer to the names above in your RGS file as
// variables %NAME% and %CLSID%, respectively.
//
// Source: http://code.google.com/p/sawbuck/source/browse/trunk/sawbuck/common/rgs_helper.h

#pragma once

#include <atlbase.h>
using namespace ATL;

struct ATLRegmapEntryHelper : public _ATL_REGMAP_ENTRY {
  ATLRegmapEntryHelper() {
    szKey = NULL;
    szData = NULL;
  }
  ATLRegmapEntryHelper(LPCOLESTR key, LPCOLESTR data) {
    szKey = key;
    size_t size = lstrlen(data) + 1;
    szData =  new wchar_t[size];
    wcscpy_s(const_cast<wchar_t*>(szData), size, data);
  }

  ATLRegmapEntryHelper(LPCOLESTR key, UINT resid) {
    wchar_t data[256] = {0};
    szKey = key;
    if (::LoadString(_pModule->m_hInstResource, resid, data, _countof(data) - 1) == 0) {
      *data = L'\0';
    }

    size_t size = lstrlen(data) + 1;

    szData = new wchar_t[size];
    wcscpy_s(const_cast<wchar_t*>(szData), size, data);
  }

  ATLRegmapEntryHelper(LPCOLESTR key, REFGUID guid) {
    szKey = key;
    static const size_t kGuidStringSize = 40;
    szData = new wchar_t[kGuidStringSize];
    if (szData) {
      if (::StringFromGUID2(guid, const_cast<LPOLESTR>(szData),
                            kGuidStringSize) == 0) {
        *const_cast<LPOLESTR>(szData) = L'\0';
      }
    }
  }
  ~ATLRegmapEntryHelper() {
    delete [] szData;
  }
};

#define BEGIN_REGISTRY_MAP(x)\
  static struct _ATL_REGMAP_ENTRY *_GetRegistryMap() {\
    static const ATLRegmapEntryHelper map[] = {
#define REGMAP_ENTRY(x, y) ATLRegmapEntryHelper(OLESTR(##x), OLESTR(##y)),

#define REGMAP_UUID(x, clsid) ATLRegmapEntryHelper(OLESTR(##x), clsid),

// This allows usage of a Resource string.
#define REGMAP_RESOURCE(x, resid) ATLRegmapEntryHelper(OLESTR(##x), resid),

// This allows usage of a static function to be called to provide the string.
#define REGMAP_FUNCTION(x, f) ATLRegmapEntryHelper(OLESTR(##x), ##f()),

#define END_REGISTRY_MAP() ATLRegmapEntryHelper() };\
    return (_ATL_REGMAP_ENTRY*)map;\
  }

#define DECLARE_REGISTRY_RESOURCEID_EX(x)\
  static HRESULT WINAPI UpdateRegistry(BOOL bRegister) {\
    return ATL::_pAtlModule->UpdateRegistryFromResource((UINT)x, bRegister, _GetRegistryMap());\
  }

/**
Use this macro if you have some registry keys that need admin rights to be written, but you do not
want to report a failure if you do not have the permissions to write them.

Params:
  rgsId: resource id of a RGS script that should always be written to the registry. Any failure
  from this script will be reported.

  adminRgsId: resource id of a RGS script that contains keys that should only be written as admin. An
  "Access Denied" failure from this script will not be reported as an error.
*/
#define DECLARE_REGISTRY_RESOURCEID_WITH_ADMIN(rgsId, adminRgsId)\
  static HRESULT WINAPI UpdateRegistry(BOOL bRegister) {\
    HRESULT hr = ATL::_pAtlModule->UpdateRegistryFromResource((UINT)adminRgsId, bRegister, _GetRegistryMap());\
	if (hr != S_OK && hr != E_ACCESSDENIED) { \
		return hr; \
	} \
    return ATL::_pAtlModule->UpdateRegistryFromResource((UINT)rgsId, bRegister, _GetRegistryMap());\
  }
