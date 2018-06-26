/**
 * The shared folder view, used to list joined shared folders on the web
 * was originally specified to be case-insensitive for UX reasons
 * Full support for UTF8 explicitly set many fields to use binary collation,
 * which was largely fine, except for the case of the shared folder view
 * Recent changes in MariaDB to fix bug related to mixed collations inside
 * views unearthed our reliance on unspecified behavior.
 * To reliably restore the intended behavior of the view, specify a case-insensitive
 * collation for fields referenced by this view and containing shared folder names.
 */
ALTER TABLE sp_shared_folder_names
    DEFAULT CHARACTER SET utf8mb4,
    MODIFY sn_name varchar(255)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;

ALTER TABLE sp_shared_folder
    MODIFY sf_public_name varchar(255)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;
