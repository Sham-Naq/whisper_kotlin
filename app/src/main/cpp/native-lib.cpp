#include <jni.h>
#include <android/log.h>

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_whisper_1kotlin_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    const char* msg = "placeholder";
    return env->NewStringUTF(msg);
}
