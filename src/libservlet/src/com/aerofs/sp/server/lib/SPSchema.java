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

            // (eric) made the columns match the SQL names here, easier to autocomplete in IDE
            // when the prefix used here matches that in the SQL schema
            T_ORGANIZATION                  = "sp_organization",
            C_O_ID                          = "o_id",
            C_O_NAME                        = "o_name",
            C_O_CONTACT_PHONE               = "o_contact_phone",
            C_O_STRIPE_CUSTOMER_ID          = "o_stripe_customer_id",

            T_OI                            = "sp_organization_invite",
            C_OI_INVITER                    = "m_from",
            C_OI_INVITEE                    = "m_to",
            C_OI_ORG_ID                     = "m_org_id",
            C_OI_SIGNUP_CODE                = "m_signup_code",

            T_DEVICE                        = "sp_device",
            C_DEVICE_ID                     = "d_id",
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

            /*
             * create table if not exists sp_acls (a_sid binary(16) not null, a_id varchar(320) not
             * null, a_role tinyint not null, primary key(a_sid, a_id), index(a_sid), index(a_id))
             * engine=InnoDB;
             */

            T_AC                            = "sp_acl",
            C_AC_STORE_ID                   = "a_sid",
            C_AC_USER_ID                    = "a_id",
            C_AC_ROLE                       = "a_role",
            C_AC_STATE                      = "a_state",
            C_AC_SHARER                     = "a_sharer",
            // see docs/design/sharing_and_migration.txt for information about this flag
            C_AC_EXTERNAL                   = "a_external",

            T_SF                            = "sp_shared_folder",
            C_SF_ID                         = "sf_id",
            C_SF_NAME                       = "sf_name",

            T_ES                            = "sp_email_subscriptions",
            C_ES_EMAIL                      = "es_email",
            C_ES_TOKEN_ID                   = "es_token_id",
            C_ES_SUBSCRIPTION               = "es_subscription",
            C_ES_LAST_EMAILED               = "es_last_emailed";
}
