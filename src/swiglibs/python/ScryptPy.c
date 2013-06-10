#include <Python.h>
#include "crypto_scrypt.h"

// format strings for parsing arguments from python and returning values to python
// see http://docs.python.org/release/1.5.2p2/ext/parseTuple.html
// and http://docs.python.org/release/1.5.2p2/ext/buildValue.html for more information
#define ARGFORMAT "ss"
#define RETURNVALFORMAT "s#"

// N, r, p, and buffer length values for use with scrypt
// Taken from com.aerofs.lib.SecUtil (at src/lib/src/com/aerofs/lib/SecUtil.java)
#define SCRYPTN 8192
#define SCRYPTR 8
#define SCRYPTP 1
#define BUFFERLENGTH 64

// boilerplate code taken from http://csl.sublevel3.org/C-functions-from-Python/
// module name is libScryptPy

static PyObject* HashError;

/**
  * Called from python as scrypt(password, salt),
  * returns scrypt hash as python string
  */
static PyObject* scrypt(PyObject* self, PyObject* args)
{
    // Note: MSVC 2010 doesn't support C99, so please stick to C89 and declare all your variables
    // at the top
    char* passwd;
    char* salt;
    size_t passwdlen, saltlen;
    uint64_t N;
    uint32_t r, p;
    char outbuf[BUFFERLENGTH];
    int retVal;

    PyArg_ParseTuple(args, ARGFORMAT, &passwd, &salt);
    passwdlen = strlen(passwd);
    saltlen = strlen(salt);
    N = SCRYPTN;
    r = SCRYPTR;
    p = SCRYPTP;

    retVal = crypto_scrypt(passwd, passwdlen, salt, saltlen, N, r, p, outbuf, BUFFERLENGTH);

    // raise an exception if retVal is nonzero (indicating the hash function failed)
    if (retVal != 0) {
        PyErr_SetString(HashError, "Hashing function failed");
        return NULL;
    }

    return Py_BuildValue(RETURNVALFORMAT, outbuf, BUFFERLENGTH);
}

static PyMethodDef libScryptPy_methods[] = {
    {"scrypt", scrypt, METH_VARARGS},
    {NULL, NULL}
};

void initlibScryptPy()
{
    PyObject* m = Py_InitModule("libScryptPy", libScryptPy_methods);
    if (m == NULL) {
        return;
    }
    // initialize HashError exception
    HashError = PyErr_NewException("libScryptPy.HashError", NULL, NULL);
    Py_INCREF(HashError);
    PyModule_AddObject(m, "HashError", HashError);
}
