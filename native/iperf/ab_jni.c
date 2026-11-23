#include <jni.h>
#include "ab_hooks.h"
JNIEXPORT void JNICALL Java_io_awarebridge_android_IperfEngine_prepare(JNIEnv *env,jclass c,jlong network) { ab_prepare((uint64_t)network); }
JNIEXPORT void JNICALL Java_io_awarebridge_android_IperfEngine_cancel(JNIEnv *env,jclass c) { ab_cancel(); }
JNIEXPORT jboolean JNICALL Java_io_awarebridge_android_IperfEngine_ready(JNIEnv *env,jclass c) { return ab_ready(); }
JNIEXPORT jstring JNICALL Java_io_awarebridge_android_IperfEngine_run(JNIEnv *env,jclass c,jboolean server,jstring host,jstring directory,jboolean udp,jboolean reverse,jint streams,jint duration,jint rate) {
    const char *temp=(*env)->GetStringUTFChars(env,directory,0);
    ab_set_temp_dir(temp);
    (*env)->ReleaseStringUTFChars(env,directory,temp);
    const char *address=(*env)->GetStringUTFChars(env,host,0);
    char *result=ab_iperf_run(server,address,"",5201,udp,reverse,streams,duration,rate);
    (*env)->ReleaseStringUTFChars(env,host,address);
    jstring text=(*env)->NewStringUTF(env,result?result:"{}");
    ab_free(result); return text;
}
