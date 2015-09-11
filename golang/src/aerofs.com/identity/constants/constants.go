package constants

// constants used in aerofs_sp database:
const AuthLevel_User int = 0
const AuthLevel_Admin int = 1
const PrivateOrgId int = 2
const InitialAclEpoch int = 1

// Properly-stored credentials must be 44 chars (SHA-256 is 32 bytes, Base64-encoded and padded makes 44).
// We use a zero-length hash (which cannot match any SHAsum) to store "no credential"
const Cred_HashLength int = 44
