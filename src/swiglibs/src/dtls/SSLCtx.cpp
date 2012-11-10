/*
 * SSLCtx.cpp
 *
 *  Created on: Jun 27, 2010
 *      Author: yuris
 */

#include "SSLCtx.h"
#include "../logger.h"
#include "dh4096.h"
#include <openssl/err.h>

SSLCtx::SSLCtx()
	: _ctx(0)
{
}

SSLCtx::~SSLCtx() {
	// TODO Auto-generated destructor stub
	if (_ctx) SSL_CTX_free(_ctx);
}

int SSLCtx::generate_cookie(SSL *ssl, unsigned char *cookie, unsigned int *cookie_len)
{
	FDEBUG( "" );
	*cookie_len = 32;
	return 1;
}

int SSLCtx::verify_cookie(SSL *ssl, unsigned char *cookie, unsigned int cookie_len)
{
	FDEBUG( "" );
	return 1;
}
int SSLCtx::init(bool isclient, char *cafile, int cafilesize, char *cert, int certsize, char *rsaprivkey, int rsaprivkeyLen)
{
	FDEBUG( " certsize = " << certsize << " cafilesize = " << cafilesize << " rsaprivkeyLen = " << rsaprivkeyLen );

    char capath[FILENAME_MAX];
	char certpath[FILENAME_MAX];

	memcpy(certpath, cert, certsize);
    memcpy(capath, cafile, cafilesize);

	certpath[certsize] = 0;
    capath[cafilesize] = 0;

	FDEBUG( " certpath = " << certpath << " capath = " << capath );

	OpenSSL_add_ssl_algorithms();
	SSL_load_error_strings();
	ERR_load_SSL_strings();

	FDEBUG( " Using DTLSv1 " );
	if (isclient) _ctx = SSL_CTX_new(DTLSv1_client_method());
	else		  _ctx = SSL_CTX_new(DTLSv1_server_method());

	if (!_ctx)
	{
		FERROR( " failed creating SSL ctx" );
		return -1;
	}

	BIO* rsaBIO = BIO_new_mem_buf((void *)rsaprivkey,rsaprivkeyLen);
	if (!rsaBIO){
		FERROR( " RSABIO == NULL" );
		return -1;
	}
	RSA* rsa = PEM_read_bio_RSAPrivateKey(rsaBIO,NULL,_ctx->default_passwd_callback,_ctx->default_passwd_callback_userdata);
	BIO_free(rsaBIO);
	if (!rsa) {
		FERROR( " RSA == NULL " );
		return -1;
	}

	if (SSL_CTX_use_RSAPrivateKey(_ctx,rsa) != 1 )
	{
		FERROR( " error loading private key" );
		FERROR( " OpenSSL returned: " << ERR_error_string(ERR_get_error(),0) );
		return -1;
	}

	if (SSL_CTX_load_verify_locations(_ctx, capath, NULL) !=  1)
	{
		FERROR( " couldn't load CA certificate" );
		FERROR( " OpenSSL returned: " << ERR_error_string(ERR_get_error(),0) );
		return -1;
	}


	//if (SSL_CTX_use_certificate_ASN1(_ctx, certsize, (const unsigned char *)cert) != 1)
	if (SSL_CTX_use_certificate_file(_ctx,certpath,SSL_FILETYPE_PEM) != 1)
	{
		FERROR( " couldn't load own certificate at " << certpath );
		FERROR( " OpenSSL returned: " << ERR_error_string(ERR_get_error(),0) );
		return -1;
	}

	if (SSL_CTX_check_private_key(_ctx) != 1)
	{
		FERROR( " private key inconsistent with certificate " );
		FERROR( " OpenSSL returned: " << ERR_error_string(ERR_get_error(),0) );
		return -1;
	}


	SSL_CTX_set_verify(_ctx, SSL_VERIFY_PEER | SSL_VERIFY_FAIL_IF_NO_PEER_CERT, &verify_callback);
	SSL_CTX_set_verify_depth(_ctx, 2); //verify certificates up to one depth

	if (!isclient)
	{
		SSL_CTX_set_cookie_generate_cb(_ctx, generate_cookie);
		SSL_CTX_set_cookie_verify_cb(_ctx, verify_cookie);
	}
	//unnecessary, this enables all "bug" workarounds, we don't actually support this anywa
	//SSL_CTX_set_options(_ctx, SSL_OP_ALL|SSL_OP_NO_SSLv2);

	//SSL_CTX_set_options(_ctx, SSL_OP_SINGLE_DH_USE);

	if (SSL_CTX_set_cipher_list(_ctx, "AES256-SHA") != 1) // DHE-RSA-AES256-SHA
	{
		FERROR( " error setting cipher list (invalid cipher AES256-SHA)" );
		return -1;
	}

//	if (SSL_CTX_set_tmp_dh(_ctx, get_dh4096()) != 1)
//	{
//		FERROR( " could not initialize DHE ");
//	}

	SSL_CTX_set_read_ahead(_ctx, 1); // Required for DTLS, "1" is from examples, not sure what it does

	//TODO: need CRL verification here
	FDEBUG( " SUCCESS " );
	return 0;
}

int SSLCtx::verify_callback( int ok , X509_STORE_CTX *store)
{

	FDEBUG("");
	//TODO: perform verifications as per example in snmp

	char data[256];

	if (!ok)
	{
		X509 *cert 	= X509_STORE_CTX_get_current_cert(store);
		int depth  	= X509_STORE_CTX_get_error_depth(store);
		int err		= X509_STORE_CTX_get_error(store);
		FWARN( " Error with certificate at depth: " << depth );

		X509_NAME_oneline(X509_get_issuer_name(cert), data, sizeof(data));
		FWARN( " issuer = " << data );

		X509_NAME_oneline(X509_get_subject_name(cert), data, sizeof(data));
		FWARN( " subject = " << data );
		FWARN( " err " << err << " : "<< X509_verify_cert_error_string(err) );

	}

	return ok;
}
