%module Scrypt
%{
#include "crypto_scrypt.h"
%}

%include "various.i"
%include "typemaps.i"
%include "../aerofs.typemaps.swg"

%apply unsigned long { uint64_t };
%apply unsigned int { uint32_t };
%apply char * BYTE { char * outbuf };
%apply char * BYTE_IN { char * passwd, char * salt};

%include "crypto_scrypt.h"
