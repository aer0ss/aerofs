package com.aerofs.sp.server.lib;

public final class SPSchema
{
    /*
     * alter table sp_user add column u_acl_epoch int not null default 0;
     */
    static public final String

            T_USER                          = "sp_user",
            C_USER_ID                       = "u_id",
            C_USER_SIGNUP_TS                = "u_id_created_ts",
            C_USER_FIRST_NAME               = "u_first_name",
            C_USER_LAST_NAME                = "u_last_name",
            C_USER_ORG_ID                   = "u_org_id",
            C_USER_AUTHORIZATION_LEVEL      = "u_auth_level",
            C_USER_CREDS                    = "u_hashed_passwd",
            C_USER_ACL_EPOCH                = "u_acl_epoch",
            C_USER_DEACTIVATED              = "u_deactivated",
            C_USER_WHITELISTED              = "u_whitelisted",
            C_USER_BYTES_USED               = "u_bytes_used",
            C_USER_USAGE_WARNING_SENT       = "u_usage_warning_sent",
            C_USER_TWO_FACTOR_ENFORCED      = "u_two_factor_enforced",
            C_USER_PASS_TS                  = "u_passwd_created_ts",

            T_ORGANIZATION                  = "sp_organization",
            C_O_ID                          = "o_id",
            C_O_NAME                        = "o_name",
            C_O_CONTACT_PHONE               = "o_contact_phone",
            C_O_QUOTA_PER_USER              = "o_quota_per_user",
            C_O_TWO_FACTOR_ENFORCEMENT_LEVEL= "o_two_factor_enforcement_level",

            T_OI                            = "sp_organization_invite",
            C_OI_INVITER                    = "m_from",
            C_OI_INVITEE                    = "m_to",
            C_OI_ORG_ID                     = "m_org_id",
            C_OI_SIGNUP_CODE                = "m_signup_code",

            T_DEVICE                        = "sp_device",
            C_DEVICE_ID                     = "d_id",
            C_DEVICE_TS                     = "d_ts",
            C_DEVICE_OS_FAMILY              = "d_os_family",
            C_DEVICE_OS_NAME                = "d_os_name",
            C_DEVICE_NAME                   = "d_name",
            C_DEVICE_OWNER_ID               = "d_owner_id",
            C_DEVICE_UNLINKED               = "d_unlinked",

            T_SIGNUP_CODE                   = "sp_signup_code",
            C_SIGNUP_CODE_CODE              = "t_code",
            C_SIGNUP_CODE_TO                = "t_to",
            C_SIGNUP_CODE_TS                = "t_ts", // auto generated

            T_CERT                          = "sp_cert",
            C_CERT_SERIAL                   = "c_serial",
            C_CERT_DEVICE_ID                = "c_device_id",
            C_CERT_EXPIRE_TS                = "c_expire_ts",
            C_CERT_REVOKE_TS                = "c_revoke_ts",

            T_PASSWORD_RESET                = "sp_password_reset_token",
            C_PASS_TOKEN                    = "r_token",
            C_PASS_USER                     = "r_user_id",
            C_PASS_TS                       = "r_ts",

            T_TWO_FACTOR_SECRET             = "sp_two_factor_secret",
            C_TWO_FACTOR_USER_ID            = "tf_u_id",
            C_TWO_FACTOR_SECRET             = "tf_secret",

            T_TWO_FACTOR_RECOVERY           = "sp_two_factor_recovery",
            C_TF_RECOVERY_ID                = "tfr_id",
            C_TF_RECOVERY_USER_ID           = "tfr_u_id",
            C_TF_RECOVERY_CODE              = "tfr_code",
            C_TF_RECOVERY_CODE_USE_TS       = "tfr_code_used_ts",

            T_AC                            = "sp_acl",
            C_AC_STORE_ID                   = "a_sid",
            C_AC_USER_ID                    = "a_id",
            C_AC_ROLE                       = "a_role",
            C_AC_STATE                      = "a_state",
            C_AC_SHARER                     = "a_sharer",
            // See docs/design/sharing_and_migration.md for information about this flag.
            C_AC_EXTERNAL                   = "a_external",
            C_AC_GID                        = "a_gid",

            T_SF                            = "sp_shared_folder",
            C_SF_ID                         = "sf_id",
            C_SF_ORIGINAL_NAME              = "sf_original_name",

            T_SFN                           = "sp_shared_folder_names",
            C_SFN_STORE_ID                  = "sn_sid",
            C_SFN_USER_ID                   = "sn_user_id",
            C_SFN_NAME                      = "sn_name",

            T_ES                            = "sp_email_subscriptions",
            C_ES_EMAIL                      = "es_email",
            C_ES_TOKEN_ID                   = "es_token_id",
            C_ES_SUBSCRIPTION               = "es_subscription",
            C_ES_LAST_EMAILED               = "es_last_emailed",

            T_US                            = "sp_url_sharing",
            C_US_KEY                        = "us_key",
            C_US_SID                        = "us_sid",
            C_US_OID                        = "us_oid",
            C_US_CREATED_BY                 = "us_created_by",
            C_US_TOKEN                      = "us_token",
            C_US_EXPIRES                    = "us_expires",
            C_US_REQUIRE_LOGIN              = "us_require_login",
            C_US_HASHED_PASSWORD            = "us_hashed_password",
            C_US_PASSWORD_SALT              = "us_password_salt",

            T_SG                            = "sp_sharing_groups",
            C_SG_GID                        = "sg_gid",
            C_SG_COMMON_NAME                = "sg_common_name",
            C_SG_ORG_ID                     = "sg_org_id",
            C_SG_EXTERNAL_ID                = "sg_external_id",

            T_GM                            = "sp_sharing_group_members",
            C_GM_GID                        = "gm_gid",
            C_GM_MEMBER_ID                  = "gm_member_id",

            T_GS                            = "sp_sharing_group_shares",
            C_GS_GID                        = "gs_gid",
            C_GS_SID                        = "gs_sid",
            C_GS_ROLE                       = "gs_role",

            T_ST                            = "sp_settings_token",
            C_ST_USER_ID                    = "st_uid",
            C_ST_TOKEN                      = "st_token",

            T_ACU                           = "sp_autocomplete_users",
            C_ACU_EMAIL                     = "acu_email",
            C_ACU_FULLNAME                  = "acu_fullname",
            C_ACU_LASTNAME                  = "acu_lastname";

}
