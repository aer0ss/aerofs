/*
 * DTLSEngine.cpp
 *
 *  Created on: Jun 26, 2010
 *      Author: yuris
 */

#include "DTLSEngine.h"
#include "../logger.h"

#define PRNG_SEED_SIZE 1024

DTLSEngine::DTLSEngine()
    : _ssl_ctx(0), _ssl(0), _bio_read(0), _bio_write(0), _hshakeDone(false)
{
    _peerCName[0] = 0;
}

DTLSEngine::~DTLSEngine()
{
	//Do not need to free _bio_read, _bio_write, SSL_free should take care of it for us

	FINFO (" _ssl = " << _ssl);
	SSL_shutdown(_ssl);
	if (_ssl) SSL_free(_ssl);
}

//DH* DTLSEngine::tmp_dh_callback(SSL *ssl, int is_export, int keylength)
//{
//	BIO* bio = BIO_new_file("../aerofs.dtls.test.certs/dh1024.pem", "r");
//	DH* dh1024 = NULL;
//	if (!bio) {
//		FWARN( "error opening dh1024.pem" );
//	} else {
//
//		DH* dh1024 = PEM_read_bio_DHparams(bio, NULL, NULL, NULL);
//
//		if (!dh1024)
//		{
//			FWARN( " error reading dh1024.pem params" );
//		}
//
//		BIO_free(bio);
//	}
//	return dh1024;
//}

DTLSEngine::DTLS_RETCODE DTLSEngine::init(bool isclient, SSLCtx *sslctx)
{
	FINFO ( "(isclient = " << isclient << ")" );
	//initialize OpenSSL
	FINFO ( " initialize OpenSSL" );
	OpenSSL_add_ssl_algorithms();
	SSL_load_error_strings();

	seed_prng(PRNG_SEED_SIZE); //seed the PRNG with PRNG_SEED_SIZE bytes of entropy

	_bio_read = BIO_new(BIO_s_mem()); // read SSL input data from
	_bio_write = BIO_new(BIO_s_mem()); // write SSL output data to

	if (!_bio_read || !_bio_write) {
		FWARN ( "BIO_new failed" );
		return DTLS_ERROR;
	}

	BIO_set_mem_eof_return(_bio_read, -1);
	BIO_set_mem_eof_return(_bio_write, -1);

	_ssl = SSL_new(sslctx->_ctx);
	if (!_ssl) {
		//TODO: error handling here
		FERROR( " _ssl creation failed" );
		return DTLS_ERROR;
	}

	SSL_set_mode(_ssl, SSL_MODE_AUTO_RETRY);

	//SSL_set_options(_ssl, SSL_OP_SINGLE_DH_USE);
	//SSL_set_tmp_dh_callback(_ssl, tmp_dh_callback);

	//XXX TODO: NEED TO IMPLEMENT COOKIE EXCHANGE!
	if(!isclient) SSL_set_options(_ssl, SSL_OP_COOKIE_EXCHANGE);

    //SSL_set_msg_callback(_ssl, &ssl_msg_callback);

	// ssl_info_callback needs it. this is a hack.
	SSL_set_msg_callback_arg(_ssl, this);

	SSL_set_info_callback(_ssl, &ssl_info_callback);

	SSL_set_bio(_ssl, _bio_read, _bio_write);	// SSL engine should read from _bio_read,
												// and write to _bio_write

	// set the SSL notion of client/server
	if (isclient)	SSL_set_connect_state(_ssl);
	else			SSL_set_accept_state(_ssl);

	return DTLS_OK;

}

void DTLSEngine::seed_prng(int size)
{
	FINFO ( "(size = " << size << ")" );

#ifdef _WIN32
	BYTE *buf = new BYTE[size]; //(BYTE*)malloc(size * sizeof(BYTE));
	HCRYPTPROV hCryptProv;

	if (!CryptAcquireContext(&hCryptProv,_T("dtlsKey"),NULL,PROV_RSA_FULL,CRYPT_NEWKEYSET) &&
		NTE_EXISTS == GetLastError()) {

			if (!CryptAcquireContext(&hCryptProv,_T("dtlsKey"),NULL,PROV_RSA_FULL,0))
			{
				FWARN( " Win32 CryptAcquireContext failed | GetLastError returned: " << GetLastError() );
			}
	}

	if (!CryptGenRandom(hCryptProv,size,buf)) FWARN( " RNG failed " );

	RAND_add(buf,size,size);

	CryptReleaseContext(hCryptProv,0);

	delete[] buf;

#else
	RAND_load_file("/dev/urandom", size); //size bytes of entropy seeded
#endif

}

void DTLSEngine::hshake_done()
{
    FINFO("");

    _hshakeDone = true;
    SSL_set_info_callback(_ssl, 0);

    // set _peerCName to empty on errors
	X509 * cert = SSL_get_peer_certificate(_ssl);
	if (!cert) {
		FERROR(" cert missing");
		_peerCName[0] = 0;
	} else {
		if (X509_NAME_get_text_by_NID(X509_get_subject_name(cert), NID_commonName,
		        _peerCName, sizeof(_peerCName)) < 0) {
		    _peerCName[0] = 0;
		}
	    X509_free(cert);
	}
}

DTLSEngine::DTLS_RETCODE DTLSEngine::encrypt(const void * input, void * output,
        int inlen, int * outsize)
{
	FINFO ("(inlen = " << inlen << ", outsize = " << *outsize << ", _ssl = " << _ssl << ")");

	DTLS_RETCODE ret = DTLS_OK;
	int rc = 0;

	//write the plaintext message to SSL
	rc = SSL_write(_ssl, input, inlen);
	FINFO ( " SSL_write rc = " << rc );

	if (-1 == rc) {
		int errnum = SSL_get_error(_ssl, rc);
		char errlog[256];
		FINFO (" SSL_write returned " << ERR_error_string(ERR_get_error(), errlog));

		if (errnum == SSL_ERROR_WANT_READ) {
			FINFO ( " errnum = SSL_ERROR_WANT_READ" );
			ret = DTLS_NEEDREAD; // need to re-try writing the data to SSL
			read_from_ssl(output, outsize);

		} else if (errnum == SSL_ERROR_WANT_WRITE) {
			FINFO ( " errnum = SSL_ERROR_WANT_WRITE" );
			ret = DTLS_NEEDWRITE;
			read_from_ssl(output, outsize);
			//todo: ssl wants to write
		} else {
			ret = DTLS_ERROR;
			*outsize = -1;
		}
	} else {
        read_from_ssl(output, outsize);
	}

	return ret;
}

DTLSEngine::DTLS_RETCODE DTLSEngine::decrypt(const void * input, void * output,
        int inlen, int * outsize)
{
	FINFO ( " ( inlen = " << inlen << ", outsize = " << *outsize << ", _ssl = "
	        << _ssl << ")" );

	DTLS_RETCODE ret = DTLS_ERROR;
	//write the encrypted code to BIO
	int rc = 0;

	verify(BIO_write(_bio_read, input, inlen) == inlen);

	//read the decrypted message from BIO
	rc = SSL_read(_ssl, output, *outsize);
	FINFO ( " SSL_read returned rc = " << rc );

	/* if the rc == -1, no actual bytes were written to the output buffer / outsize */
	if (-1 == rc) {
		int errnum = SSL_get_error(_ssl, rc);

		if (errnum == SSL_ERROR_WANT_READ) {
			FINFO ( "errnum = SSL_ERROR_WANT_READ" );
			ret = DTLS_NEEDREAD;
			read_from_ssl(output, outsize);

			/*if (*outsize <= 0) {
				FINFO ( "SSL session needs to renegotiate -- call SSL_clear()" );
				SSL_clear(_ssl);
				FINFO ( "Retrying SSL_read" );
				*outsize = SSL_read(_ssl, output, *outsize);
				FINFO ( "SSL_read returned outsize = " << *outsize );

			}*/
		} else if (errnum == SSL_ERROR_WANT_WRITE) {
			FINFO ( "errnum = SSL_ERROR_WANT_WRITE" );
			ret = DTLS_NEEDWRITE;
			read_from_ssl(output, outsize);
		} else {
			char errlog[256];
			ERR_error_string(ERR_get_error(), errlog);

			//TODO: log ssl write error
			FWARN( " errnum = " << errnum << " | errlog = " << errlog);

			ret = DTLS_ERROR;
		}
	} else {
		// rc != -1
		*outsize = rc;
		ret = DTLS_OK;
	}
	return ret;
}

/**
 * This function checks for any queued packets in the BIO buffer
 *
 */
void DTLSEngine::read_from_ssl(void *output, int *outsize)
{
	FINFO ("output buffer size = " << *outsize);

	*outsize = BIO_read(_bio_write, output, *outsize);
	if (*outsize <= 0) {
		FINFO (" BIO_read returned " << *outsize);
	} else {
		FINFO (" BIO_read returned buffer of size " << *outsize);
	}
}

/*
void DTLSEngine::ssl_msg_callback(int write_p, int version, int content_type,
        const void *buf, size_t len, SSL *ssl, void *arg)
{
	FINFO ( " obj " << ssl <<
			  " state: " << SSL_state_string_long(ssl)<<
			  " in_hshake: " << ssl->in_handshake <<
			  " recv(0)/sent(1)?" << write_p <<
			  " ctype: " << content_type <<
			  " len = " << len );
}
*/

void DTLSEngine::ssl_info_callback(const SSL* s, int __where, int ret)
{
	const char *str;
	int w;
	w = __where & ~SSL_ST_MASK;

	if (w & SSL_ST_CONNECT) str = " SSL_connect ";
	else if (w & SSL_ST_ACCEPT) str = " SSL_accept ";
	else str = " undefined ";

	if (__where & SSL_CB_LOOP)
	{
			FINFO ( str << SSL_state_string_long(s) );
	}
	else if (__where & SSL_CB_ALERT)
	{
			str = (__where & SSL_CB_READ) ? " read " : " write ";
			FINFO ( " SSL3 alert" << str << SSL_alert_type_string_long(ret) << ": " << SSL_alert_desc_string_long(ret) );
	}
	else if (__where & SSL_CB_EXIT)
	{
		if (ret == 0)
		{
            FINFO( str << "failed in "<< SSL_state_string_long(s) );
		}
		else if (ret < 0)
		{
			FINFO( str << "error in " << SSL_state_string_long(s) );
		}
	} else {
		FINFO ( str << SSL_state_string_long(s) );
	}

	DTLSEngine * engine = (DTLSEngine *) s->msg_callback_arg;

	if (strcmp("SSL negotiation finished successfully", SSL_state_string_long(s)) == 0)
	{
		engine->hshake_done();
	}
}

int DTLSEngine::getPeerCName(void * buf, int len)
{
#ifdef _WIN32
    // disable the warning of "'strncpy': This function or variable may be unsafe"
    #pragma warning(disable : 4996)
#endif
    strncpy((char *) buf, _peerCName, len);
#ifdef _WIN32
    #pragma warning(default: 4996)
#endif

    int slen = strlen(_peerCName);
    if (slen >= len) ((char *) buf)[len - 1] = 0;
    return slen;
}
