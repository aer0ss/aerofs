#ifndef LAUNCHER_H
#define LAUNCHER_H

bool launcher_create_jvm(const char* approot, char** args, JavaVM** pjvm, JNIEnv** penv, char** perrmsg);
int launcher_launch(JNIEnv* env, char** perrmsg);
void launcher_destroy_jvm(JavaVM* jvm);

#endif // LAUNCHER_H
