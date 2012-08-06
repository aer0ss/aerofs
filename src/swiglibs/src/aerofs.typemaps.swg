
/**
 * copied from common.swg since libjingle.binding should be considered a separate project
 */
%typemap(jni) char *BYTE_IN "jbyteArray"
%typemap(jtype) char *BYTE_IN "byte[]"
%typemap(jstype) char *BYTE_IN "byte[]"
%typemap(in) char *BYTE_IN {
    $1 = (char *) JCALL2(GetByteArrayElements, jenv, $input, 0);
}
%typemap(argout) char *BYTE_IN {
    JCALL3(ReleaseByteArrayElements, jenv, $input, (jbyte *) $1, JNI_ABORT);
}
%typemap(javain) char *BYTE_IN "$javainput"
/* Prevent default freearg typemap from being used */
%typemap(freearg) char *BYTE_IN ""

/**
 * type JNI. refer to typemap "char *BYTE" in various.i
 */
%typemap(jni) jstring JNI "jstring"
%typemap(jtype) jstring JNI "String"
%typemap(jstype) jstring JNI "String"
%typemap(in) jstring JNI "$1 = $input;"

%typemap(argout) jstring JNI ""

%typemap(javain) jstring JNI "$javainput"

/* Prevent default freearg typemap from being used */
%typemap(freearg) jstring JNI ""

%typemap(jni) JNIEnv *JNI "void *"
%typemap(jtype) JNIEnv *JNI "Object"
%typemap(jstype) JNIEnv *JNI "Object"
%typemap(in) JNIEnv *JNI "$1 = jenv;"

%typemap(argout) JNIEnv *JNI ""

%typemap(javain) JNIEnv *JNI "$javainput"

/* Prevent default freearg typemap from being used */
%typemap(freearg) JNIEnv *JNI ""


/**
 * type NULLABLE_BYTE. refer to typemap "char *BYTE" in various.i
 */
%typemap(jni) char *NULLABLE_BYTE "jbyteArray"
%typemap(jtype) char *NULLABLE_BYTE "byte[]"
%typemap(jstype) char *NULLABLE_BYTE "byte[]"
%typemap(in) char *NULLABLE_BYTE {
    $1 = $input ? (char *) JCALL2(GetByteArrayElements, jenv, $input, 0) : 0;
}

%typemap(argout) char *NULLABLE_BYTE {
    if ($input) JCALL3(ReleaseByteArrayElements, jenv, $input, (jbyte *) $1, 0);
}

%typemap(javain) char *NULLABLE_BYTE "$javainput"

/* Prevent default freearg typemap from being used */
%typemap(freearg) char *NULLABLE_BYTE ""
