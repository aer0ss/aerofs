/*
 * SSLCtx.h
 *
 *  Created on: Jun 27, 2010
 *      Author: yuris
 */

#ifndef SSLCTX_H_
#define SSLCTX_H_

#ifdef _WIN32
#include <Windows.h> // we must include the windows header before any openssl header in order to avoid name clashes
#endif
#include <openssl/ssl.h>
#include "../logger.h"



class SSLCtx {
private:

	static int verify_callback( int ok , X509_STORE_CTX *store);

	static int verify_cookie(SSL *ssl, unsigned char *cookie, unsigned int cookie_len);
	static int generate_cookie(SSL *ssl, unsigned char *cookie, unsigned int *cookie_len);
public:
	SSL_CTX* _ctx;

	SSLCtx();
	int init(bool isclient, char *cafile, int cafilesize, char *cert, int certsize, char *rsaprivkey, int rsaprivkeyLen) ;
	virtual ~SSLCtx();
};

#endif /* SSLCTX_H_ */
