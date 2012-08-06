# NOTE If running OpenSSL < 0.9.8a, see -newkey, below.
#
# Automates the setup of a custom Certificate Authority and provides
# routines for signing and revocation of certificates. To use, first
# customize the commands in this file and the settings in openssl.cnf,
# then run:
#
# make init
#
# Then, copy in certificate signing requests, and ensure their suffix is
# .csr before signing them with the following command:
#
# make sign
#
# To revoke a key, name the certificate file with the cert option
# as shown below:
#
# make revoke cert=foo.cert
#
# This will revoke the certificate and call gencrl; the revocation list
# will then need to be copied somehow to the various systems that use
# your CA cert.
#
# This Makefile has some other useful functions, including 'make peekcrl', which
# print the CRL in human readable form. Run 'make help' for details.

requests = *.csr

sign: ${requests}

# remove -batch option if want chance to not certify a particular request
${requests}: FORCE
	@openssl ca -batch -config openssl.cnf -in $@ -out ${@:.csr=.cert}
	@[ -f ${@:.csr=.cert} ] && rm $@

revoke:
	@test $${cert:?"usage: make revoke cert=<cert>"}
	@openssl ca -config openssl.cnf -revoke $(cert)
	@$(MAKE) gencrl

gencrl:
	@openssl ca -config openssl.cnf -gencrl -out ca-crl.pem

peekcrl:
	@openssl crl -text -noout -in ca-crl.pem

clean:
	@rm -f ${requests}

purge: clean
	@rm -f ca-cert.pem serial *cert index* *.old *.pem
	@rm -rf crl certs newcerts private

# creates required supporting files, CA key and certificate
init:
	@if [ -f serial ]; then echo "Can't init twice, purge first."; exit 1; fi
	@mkdir -p crl certs newcerts private
	@chmod go-rwx private
	@echo '01' > serial
	@touch index
	# NOTE use "-newkey rsa" if running OpenSSL less than 0.9.8a
	@openssl req -config openssl.cnf -days 365 -x509 -newkey rsa:2048 -out ca-cert.pem -outform PEM
	@$(MAKE) gencrl

help:
	@echo make sign
	@echo '  - signs all *.csr files in this directory'
	@echo
	@echo 'make revoke cert=<cert>'
	@echo '  - revokes certificate in named file and calls gencrl'
	@echo
	@echo make gencrl
	@echo '  - updates Certificate Revocation List (CRL)'
	@echo
	@echo make peekcrl
	@echo '  - peek at the Certificate Revocation List (CRL)'
	@echo
	@echo make clean
	@echo '  - removes all *.csr files in this directory'
	@echo
	@echo make purge
	@echo '  - remove all generated files in this directory'

# for legacy make support
FORCE:
