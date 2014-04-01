Convert Authenticode signing key to PVK format
===

General instructions from http://www.drh-consultancy.demon.co.uk/pkcs12faq.html

The `signcode` tool expects certificates and keys in a format that is not the usual one used by openssl and other common crypto tools.

## Convert certificate chain from PEM to SPC

The Mono toolchain provides the `cert2spc` tool for this purpose.  If you've installed Mono, you should be set.  Note that the input cert file must contain the complete PEM-encoded, ASCII-armored certificate chain, and must be named *.cer.

```
cert2spc path/to/cert.cer path/to/output/cert.spc
```

## Convert key from PEM to PVK

The PVK format is a pain in the rear.  There is supposedly some work in OpenSSL to do this conversion, but it does not appear to work upstream.

Instead, we'll use the pvk tools from http://www.drh-consultancy.demon.co.uk/ .  We have to build them from source, so make sure you have OpenSSL headers installed.

```
mkdir pvksrc
cd pvksrc
wget http://www.drh-consultancy.demon.co.uk/pvksrc.tgz.bin -O pvksrc.tar.gz
tar xzvf prksrc.tar.gz
make
./pvk -topvk -strong -in path/to/pem/privateKey.key -out path/to/output/privateKey.pvk
```