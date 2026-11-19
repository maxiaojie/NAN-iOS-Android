#include <jni.h>
#include "ab_hooks.h"
JNIEXPORT void JNICALL Java_io_awarebridge_android_IperfServer_prepare(JNIEnv *env,jclass c,jlong network) { ab_prepare((uint64_t)network); }
JNIEXPORT void JNICALL Java_io_awarebridge_android_IperfServer_cancel(JNIEnv *env,jclass c) { ab_cancel(); }
JNIEXPORT jstring JNICALL Java_io_awarebridge_android_IperfServer_run(JNIEnv *env,jclass c,jstring host,jstring directory) {
    const char *temp=(*env)->GetStringUTFChars(env,directory,0);
    ab_set_temp_dir(temp);
    (*env)->ReleaseStringUTFChars(env,directory,temp);
    const char *address=(*env)->GetStringUTFChars(env,host,0);
    char *result=ab_iperf_run(1,address,"",5201,0,0,4,10,50);
    (*env)->ReleaseStringUTFChars(env,host,address);
    jstring text=(*env)->NewStringUTF(env,result?result:"{}");
    ab_free(result); return text;
}

JNIEXPORT jboolean JNICALL Java_io_awarebridge_android_IperfServer_ready(JNIEnv *env,jclass c) { return ab_ready(); }
