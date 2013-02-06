#ifndef __PKCS10_H__
#define __PKCS10_H__

#ifdef __cplusplus
extern "C" {
#endif

/* Opaque request context handle.  API consumers will only ever interact with a
 * pointer to this type. */
struct _req_context;
typedef struct _req_context req_context;

/**
 * Create a new PKCS10 request context.
 * @return NULL if no memory or OpenSSL internal failure, otherwise a newly
 * allocated request context.
 */
req_context* req_init();

/**
 * Set the public exponent for the PKCS10 context's RSA key.
 * @param ctx PKCS10 context on which to operate
 * @param buf A buffer of len bytes containing the big-endian, two's-complement
              representation of the RSA key's public exponent.
 * @param len The number of bytes in buf.
 * @return 0 on success, -1 on failure
 */
int req_set_public_exponent(req_context* ctx, void* buf, int len);

/**
 * Set the private exponent for the PKCS10 context's RSA key.
 * @param ctx PKCS10 context on which to operate
 * @param buf A buffer of len bytes containing the big-endian, two's-complement
              representation of the RSA key's private exponent.
 * @param len The number of bytes in buf.
 * @return 0 on success, -1 on failure
 */
int req_set_private_exponent(req_context* ctx, void* buf, int len);

/**
 * Set the modulus for the PKCS10 context's RSA key.
 * @param ctx PKCS10 context on which to operate
 * @param buf A buffer of len bytes containing the big-endian, two's-complement
              representation of the RSA key's modulus.
 * @param len The number of bytes in buf.
 * @return 0 on success, -1 on failure
 */
int req_set_modulus(req_context* ctx, void* buf, int len);

/**
 * Set the CNAME for the PKCS10 request.
 * @param ctx PKCS10 context on which to operate
 * @param buf A buffer of len bytes containing a UTF-8 encoded string of the
 *            desired CNAME for the request.
 * @param len The number of bytes in buf.
 * @return 0 on success, -1 on failure
 */
int req_set_cname(req_context* ctx, void* buf, int len);

/**
 * Sign the CSR and return the number of bytes needed to contain the PKCS10
 * DER-encoded request.
 * @param ctx PKCS10 context on which to operate
 * @return < 0 on error, otherwise, the length of the CSR, in bytes.
 * In either case, the only valid subsequent operations after calling
 * req_sign() are req_get_csr_bytes() and req_free().
 */
int req_sign(req_context* ctx);

/**
 * Export the DER-encoded Certificate Signing Request into the provided buffer.
 * @param buf Output buffer into which the CSR should be copied
 * @param len Size of output buffer
 * @return 0 on success, < 0 on error
 */
// Copies the csr into buf (which the caller guarantees is len bytes long)
int req_get_csr_bytes(req_context* ctx, void* buf, int len);

/**
 * Free the PKCS10 context and all associated data.
 */
void req_free(req_context* ctx);

#ifdef __cplusplus
}
#endif

#endif /* __PKCS10_H__ */
