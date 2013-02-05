%module PKCS10
%{
#include "PKCS10.h"
%}

%include "../aerofs.typemaps.swg"
%include "various.i"
%include "typemaps.i"

%apply char * BYTE_IN { const void * };
%apply char * BYTE { void * };
%apply int * INOUT { int * outsize };

%include "PKCS10.h"
