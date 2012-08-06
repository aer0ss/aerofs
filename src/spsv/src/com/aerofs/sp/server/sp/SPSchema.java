package com.aerofs.sp.server.sp;

final class SPSchema
{
    /*
     * alter table sp_user add column u_acl_epoch int not null default 0;
     */

    static final String T_USER                         = "sp_user";
    static final String C_USER_ID                      = "u_id";
    static final String C_USER_FIRST_NAME              = "u_first_name";
    static final String C_USER_LAST_NAME               = "u_last_name";
    static final String C_USER_ORG_ID                  = "u_org_id";
    static final String C_USER_AUTHORIZATION_LEVEL     = "u_auth_level";
    static final String C_USER_CREDS                   = "u_hashed_passwd";
    static final String C_USER_PASSWORD_RESET_TOKEN    = "u_passwd_reset_token";
    static final String C_FINALIZED                    = "u_finalized";
    static final String C_USER_VERIFIED                = "u_verified";
    static final String C_USER_STORELESS_INVITES_QUOTA = "u_storeless_invites_quota";
    static final String C_USER_ACL_EPOCH               = "u_acl_epoch";

    static final String T_ORGANIZATION                 = "sp_organization";
    static final String C_ORG_ID                       = "o_id";
    static final String C_ORG_NAME                     = "o_name";
    static final String C_ORG_ALLOWED_DOMAIN           = "o_allowed_domain";
    static final String C_ORG_OPEN_SHARING             = "o_open_sharing";

    static final String T_DEVICE                       = "sp_device";
    static final String C_DEVICE_ID                    = "d_id";
    static final String C_DEVICE_NAME                  = "d_name";
    static final String C_DEVICE_OWNER_ID              = "d_owner_id";

    // TODO rename TI BI and FI to reflect new table/column names
    static final String T_TI                           = "sp_signup_code";
    static final String C_TI_TIC                       = "t_code";
    static final String C_TI_FROM                      = "t_from";
    static final String C_TI_TO                        = "t_to";
    static final String C_TI_ORG_ID                    = "t_org_id";

    static final String T_CERT                         = "sp_cert";
    static final String C_CERT_SERIAL                  = "c_serial";
    static final String C_CERT_DEVICE_ID               = "c_device_id";
    static final String C_CERT_EXPIRE_TS               = "c_expire_ts";
    static final String C_CERT_REVOKE_TS               = "c_revoke_ts";

    static final String T_BI                           = "sp_batch_signup_code";
    static final String C_BI_IDX                       = "b_idx";
    static final String C_BI_BIC                       = "b_code";
    static final String C_BI_USER                      = "b_user";

    static final String T_FI                           = "sp_shared_folder_code";
    static final String C_FI_FIC                       = "f_code";
    static final String C_FI_FROM                      = "f_from";
    static final String C_FI_TO                        = "f_to";
    static final String C_FI_SID                       = "f_share_id";
    static final String C_FI_FOLDER_NAME               = "f_folder_name";

    static final String T_PASSWORD_RESET               = "sp_password_reset_token";
    static final String C_PASS_TOKEN                   = "r_token";
    static final String C_PASS_USER                    = "r_user_id";
    static final String C_PASS_TS                      = "r_ts";
    static final String C_PASS_VALID                   = "r_valid";

    /*
     * create table if not exists sp_acls (a_sid binary(16) not null, a_id varchar(320) not null,
     * a_role tinyint not null, primary key(a_sid, a_id), index(a_sid), index(a_id)) engine=InnoDB;
     */

    static final String T_AC                           = "sp_acl";
    static final String C_AC_STORE_ID                  = "a_sid";
    static final String C_AC_USER_ID                   = "a_id";
    static final String C_AC_ROLE                      = "a_role";
}
