/*
 * DTLSEngine.h
 *
 *  Created on: Jun 26, 2010
 *      Author: yuris
 */

#ifndef DTLS_H_
#define DTLS_H_

#ifdef _WIN32
#include <Windows.h> // we must include the windows header before any openssl header in order to avoid name clashes
#endif
#include <openssl/ssl.h>
#include <openssl/bio.h>
#include <openssl/rand.h>
#include <openssl/err.h>
#include <openssl/x509v3.h>
#include "SSLCtx.h"


class DTLSEngine {
public:
	enum DTLS_RETCODE {
		DTLS_NEEDREAD,
		DTLS_NEEDWRITE,
		DTLS_OK,
		DTLS_ERROR
	};
private:
	SSLCtx * _ssl_ctx;
	SSL * _ssl;

	BIO * _bio_read;    // BIO for OpenSSL to read incoming packets
	BIO * _bio_write;   // BIO for OpenSSL to write incoming packets into

	bool _hshakeDone;

	// 64 is the length of common names used by AeroFS (hex-encoded sha-256)
    char _peerCName[65];

	void seed_prng(int size);
	void read_from_ssl(void *output, int *outsize);
    void hshake_done();

	static DH* tmp_dh_callback(SSL *ssl, int is_export, int keylength);
	static void ssl_info_callback(const SSL *s, int __where, int ret);

public:
	DTLSEngine();

    virtual ~DTLSEngine();

	DTLS_RETCODE init(bool isclient, SSLCtx *sslctx);

	DTLS_RETCODE encrypt(const void * input, void * output, int inlen,
	        int * outsize);

	DTLS_RETCODE decrypt(const void * input, void * output, int inlen,
	        int * outsize);

	// return the length of the common name. the returned buffer is guaranteed
	// to be null terminated. may be empty if isHshakeDone() is false or any
	// error occurred when retrieving the common name.
	//
	int getPeerCName(void * buf, int len);

	bool isHshakeDone()
	{
        return _hshakeDone;
	}
};

//void test(char *test);
#endif /* DTLS_H_ */
