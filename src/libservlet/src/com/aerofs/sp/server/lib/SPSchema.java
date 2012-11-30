package com.aerofs.sp.server.lib;

public final class SPSchema
{
    /*
     * alter table sp_user add column u_acl_epoch int not null default 0;
     */
    static public final String

            T_USER                          = "sp_user",
            C_USER_ID                       = "u_id",
            C_USER_FIRST_NAME               = "u_first_name",
            C_USER_LAST_NAME                = "u_last_name",
            C_USER_ORG_ID                   = "u_org_id",
            C_USER_AUTHORIZATION_LEVEL      = "u_auth_level",
            C_USER_CREDS                    = "u_hashed_passwd",
            C_USER_VERIFIED                 = "u_verified",
            C_USER_STORELESS_INVITES_QUOTA  = "u_storeless_invites_quota",
            C_USER_ACL_EPOCH                = "u_acl_epoch",

            T_ORG                           = "sp_organization",
            C_ORG_ID                        = "o_id",
            C_ORG_NAME                      = "o_name",

            T_DEVICE                        = "sp_device",
            C_DEVICE_ID                     = "d_id",
            C_DEVICE_NAME                   = "d_name",
            C_DEVICE_OWNER_ID               = "d_owner_id",
            CONSTRAINT_DEVICE_NAME_OWNER    = "d_name_owner",

            // TODO rename TI BI and FI to reflect new table/column names
            T_TI                            = "sp_signup_code",
            C_TI_TIC                        = "t_code",
            C_TI_FROM                       = "t_from",
            C_TI_TO                         = "t_to",
            C_TI_ORG_ID                     = "t_org_id",
            C_TI_TS                         = "t_ts", //auto generated

            T_CERT                          = "sp_cert",
            C_CERT_SERIAL                   = "c_serial",
            C_CERT_DEVICE_ID                = "c_device_id",
            C_CERT_EXPIRE_TS                = "c_expire_ts",
            C_CERT_REVOKE_TS                = "c_revoke_ts",

            T_FI                            = "sp_shared_folder_code",
            C_FI_FIC                        = "f_code",
            C_FI_FROM                       = "f_from",
            C_FI_TO                         = "f_to",
            C_FI_SID                        = "f_share_id",
            C_FI_FOLDER_NAME                = "f_folder_name",
            C_FI_ROLE                       = "f_role",

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

            T_SF                            = "sp_shared_folder",
            C_SF_ID                         = "sf_id",
            C_SF_NAME                       = "sf_name",

            T_ES                            = "sp_email_subscriptions",
            C_ES_EMAIL                      = "es_email",
            C_ES_TOKEN_ID                   = "es_token_id",
            C_ES_SUBSCRIPTION               = "es_subscription",
            C_ES_LAST_EMAILED               = "es_last_emailed";
}
