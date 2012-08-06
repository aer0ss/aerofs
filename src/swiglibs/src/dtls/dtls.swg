%module DTLS
%{
#include "SSLCtx.h"
#include "DTLSEngine.h"
%}

%include "../aerofs.typemaps.swg"
%include "various.i"
%include "typemaps.i"

%apply char * BYTE_IN { const void * };
%apply char * BYTE { void * };
%apply int * INOUT { int * outsize };

%include "SSLCtx.h"
%include "DTLSEngine.h"

