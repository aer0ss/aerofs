#include <openssl/bn.h>
#include <openssl/evp.h>
#include <openssl/x509.h>
#include <openssl/rsa.h>
#include <stdio.h>
#include <string.h>

unsigned char* ORG_UNIT      = (unsigned char*)"na";
unsigned char* ORG_NAME      = (unsigned char*)"aerofs.com";
unsigned char* STATE_NAME    = (unsigned char*)"CA";
unsigned char* COUNTRY_NAME  = (unsigned char*)"US";
unsigned char* LOCALITY_NAME = (unsigned char*)"SF";

typedef struct {
    RSA* rsa;
    EVP_PKEY* envelope;
    X509_REQ* req;
    unsigned char* cname;
    unsigned char* csr_bytes;
    int csr_bytes_len;
    unsigned char n_set;
    unsigned char e_set;
    unsigned char d_set;
    unsigned char cname_set;
} req_context;

/* Java doesn't have null-terminated strings, and even String.getBytes() lacks
 * a null terminator so we make one here, since we're passing NULL-terminated
 * UTF8 to OpenSSL.
 */
static char* add_null_terminator(char* buf, int len)
{
    /* N.B. all declarations are separate because Windows requires C89 */
    char* retval;
    retval = malloc(len+1);
    if (retval != NULL) {
        memcpy(retval, buf, len);
        /* Ensure null terminator exists */
        retval[len] = '\0';
    }
    return retval;
}

static BIGNUM* bn_from_java_bytearray(void* buf, int len)
{
    return BN_bin2bn(buf, len, NULL);
}

req_context* req_init()
{
    req_context* ctx;
    OpenSSL_add_all_algorithms();
    ctx = malloc(sizeof(req_context));
    if (ctx != NULL) {
        memset(ctx, 0, sizeof(*ctx));
        ctx->rsa = RSA_new();
        if (!ctx->rsa) {
            goto req_init_err1;
        }
        if ((ctx->req = X509_REQ_new()) == NULL) {
            goto req_init_err2;
        }
        if ((ctx->envelope = EVP_PKEY_new()) == NULL) {
            goto req_init_err3;
        }
    }
    return ctx;
    /* Unroll allocations and return NULL */
req_init_err3:
    X509_REQ_free(ctx->req);
req_init_err2:
    RSA_free(ctx->rsa);
req_init_err1:
    free(ctx);
    return NULL;
}

int req_set_public_exponent(req_context* ctx, void* buf, int len)
{
    if (ctx->e_set) return -1;
    ctx->rsa->e = bn_from_java_bytearray(buf, len);
    if (ctx->rsa->e == NULL) return -1;
    ctx->e_set = 1;
    return 0;
}

int req_set_private_exponent(req_context* ctx, void* buf, int len)
{
    if (ctx->d_set) return -1;
    ctx->rsa->d = bn_from_java_bytearray(buf, len);
    if (ctx->rsa->d == NULL) return -1;
    ctx->d_set = 1;
    return 0;
}

int req_set_modulus(req_context* ctx, void* buf, int len)
{
    if (ctx->n_set) return -1;
    ctx->rsa->n = bn_from_java_bytearray(buf, len);
    if (ctx->rsa->n == NULL) return -1;
    ctx->n_set = 1;
    return 0;
}

int req_set_cname(req_context* ctx, void* buf, int len)
{
    if (ctx->cname_set) return -1;
    ctx->cname = (unsigned char*)add_null_terminator(buf, len);
    if (ctx->cname == NULL) return -1;
    ctx->cname_set = 1;
    return 0;
}

/**
 * Returns < 0 on error, length of the CSR, in bytes, otherwise.
 * In either case, the only valid subsequent operations after calling
 * req_sign() are req_get_csr_bytes() and req_free().
 */
int req_sign(req_context* ctx)
{
    X509_NAME* name;
    /* Precondition checks:
     * Verify that the context is in a state suitable for signing:
     *  - if csr_bytes is not null, we've already signed this req.
     *  - otherwise, we lack necessary parameters for signing.
     */
    if (ctx->csr_bytes != NULL ||
            !(ctx->n_set && ctx->e_set && ctx->d_set && ctx->cname_set)) {
        return -1;
    }

    /* Use envelope to hold the RSA* for crypto operations */
    if (!EVP_PKEY_set1_RSA(ctx->envelope, ctx->rsa)) {
        EVP_PKEY_free(ctx->envelope);
        return -1;
    }
    /* Now the RSA* is owned by the EVP_PKEY* and will be freed by
     * EVP_PKEY_free().  Release our handle to it, so we don't try to
     * double-free it.
     */
    ctx->rsa = NULL;

    /* Set request parameters */
    /* Set PKCS10 version 1. ASN.1 INTEGER (0) is version 1. */
    if (!X509_REQ_set_version(ctx->req, 0L)) {
        return -1;
    }
    /* Set subject fields */
    name = X509_REQ_get_subject_name(ctx->req);
    /* Example:
     * Subject: C=US, ST=CA, L=SF, O=aerofs.com, OU=na,
     * CN=aofpkbglginfadbnkomomlkachpblhegpololahjhmbedfhimikbleedhbcebdjf */
    X509_NAME_add_entry_by_txt(name, "C",  MBSTRING_UTF8, COUNTRY_NAME, -1, -1, 0);
    X509_NAME_add_entry_by_txt(name, "ST", MBSTRING_UTF8, STATE_NAME, -1, -1, 0);
    X509_NAME_add_entry_by_txt(name, "L",  MBSTRING_UTF8, LOCALITY_NAME, -1, -1, 0);
    X509_NAME_add_entry_by_txt(name, "O",  MBSTRING_UTF8, ORG_NAME, -1, -1, 0);
    X509_NAME_add_entry_by_txt(name, "OU", MBSTRING_UTF8, ORG_UNIT, -1, -1, 0);
    X509_NAME_add_entry_by_txt(name, "CN", MBSTRING_UTF8, ctx->cname, -1, -1, 0);

    /* Set the public key and sign. */
    X509_REQ_set_pubkey(ctx->req, ctx->envelope);
    if (!X509_REQ_sign(ctx->req, ctx->envelope, EVP_sha1())) {
        return -1;
    }

    /* Export csr from OpenSSL-internal representation to DER-encoded
     * bytestring in this context's csr_bytes field and return csr length. */
    ctx->csr_bytes_len = i2d_X509_REQ(ctx->req, &ctx->csr_bytes);
    return ctx->csr_bytes_len;
}

/* Copies the csr into buf (which the caller guarantees is len bytes long) */
int req_get_csr_bytes(req_context* ctx, void* buf, int len)
{
    if (len < ctx->csr_bytes_len || ctx->csr_bytes == NULL) {
        return -1; /* Buffer too small or req_sign() not called or failed */
    }
    memcpy(buf, ctx->csr_bytes, ctx->csr_bytes_len);
    return 0;
}

void req_free(req_context* ctx)
{
    if (ctx->rsa) {
        RSA_free(ctx->rsa);
    }
    if (ctx->envelope) {
        EVP_PKEY_free(ctx->envelope);
    }
    if (ctx->req) {
        X509_REQ_free(ctx->req);
    }
    if (ctx->cname) {
        free(ctx->cname);
    }
    if (ctx->csr_bytes) {
        free(ctx->csr_bytes);
    }
    memset(ctx, 0, sizeof(*ctx));
    free(ctx);
}
