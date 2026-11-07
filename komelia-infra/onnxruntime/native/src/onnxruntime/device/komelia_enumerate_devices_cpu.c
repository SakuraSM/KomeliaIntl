#include <jni.h>

JNIEXPORT jobject JNICALL
Java_snd_komelia_onnxruntime_JvmOnnxRuntime_enumerateDevices(JNIEnv *env, jobject self) {
    (void) self;
    jclass collections = (*env)->FindClass(env, "java/util/Collections");
    jmethodID empty_list = (*env)->GetStaticMethodID(env, collections, "emptyList", "()Ljava/util/List;");
    return (*env)->CallStaticObjectMethod(env, collections, empty_list);
}
