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
requests = *.csr

sign: ${requests}

# remove -batch option if want chance to not certify a particular request
${requests}: FORCE
	@openssl ca -batch -config openssl.cnf -in $@ -out ${@:.csr=.cert}
	@[ -f ${@:.csr=.cert} ] && rm $@

clean:
	@rm -f ${requests}

purge: clean
	@rm -f ca-cert.pem serial *cert index* *.old *.pem
	@rm -rf certs newcerts private

# Creates required supporting files, CA key and certificate.
# CA certificate has 10 year expiry.
init:
	@if [ -f serial ]; then echo "Can't init twice, purge first."; exit 1; fi
	@echo ">>> Initialize CA Server"
	@mkdir -p certs newcerts private
	@chmod go-rwx private
	@echo '01' > serial
	@touch index
	@openssl req -config openssl.cnf -days 3650 -x509 -newkey rsa:2048 -out ca-cert.pem -outform PEM
	@echo Success!

help:
	@echo make sign
	@echo '  - signs all *.csr files in this directory'
	@echo
	@echo make clean
	@echo '  - removes all *.csr files in this directory'
	@echo
	@echo make purge
	@echo '  - remove all generated files in this directory'
