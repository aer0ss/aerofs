package com.aerofs.sv.server;

class SVSchema {

    static final String C_HDR_CLIENT    = "hdr_client";
    static final String C_HDR_TS        = "hdr_ts";
    static final String C_HDR_VER       = "hdr_ver";
    static final String C_HDR_USER      = "hdr_user";
    static final String C_HDR_DID       = "hdr_did";
    static final String C_HDR_APPROOT   = "hdr_approot";
    static final String C_HDR_RTROOT    = "hdr_rtroot";

    static final String T_DEF           = "sv_defect";
    static final String C_DEF_AUTO      = "def_auto";
    static final String C_DEF_DESC      = "def_desc";
    static final String C_DEF_CFG       = "def_cfg";
    static final String C_DEF_JAVA_ENV  = "def_java_env";

    static final String T_EV            = "sv_event";
    static final String C_EV_TYPE       = "ev_type";
    static final String C_EV_ALIAS      = "ev_alias";
    static final String C_EV_DESC       = "ev_desc";

    static final String T_EE            = "email_event";
    static final String C_EE_ID         = "ee_id";
    static final String C_EE_EMAIL      = "ee_email";
    static final String C_EE_EVENT      = "ee_event";
    static final String C_EE_DESC       = "ee_desc";
    static final String C_EE_CATEGORY   = "ee_category";
    static final String C_EE_TS         = "ee_ts";
}
