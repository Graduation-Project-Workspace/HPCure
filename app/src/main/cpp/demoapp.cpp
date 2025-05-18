// Write C++ code here.
#include <jni.h>

//
// Do not forget to dynamically load the C++ library into your application.
//
// For instance,
//
// In MainActivity.java:
//    static {
//       System.loadLibrary("demoapp");
//    }
//
// Or, in MainActivity.kt:
//    companion object {
//      init {
//         System.loadLibrary("demoapp")
//      }
//    }
extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_demoapp_HomeScreen_doOps(JNIEnv *env, jobject thiz, jlong ops) {
    long sum = 0;
    for (int i = 0; i < ops; i++) {
        sum += i;
    }
    return sum;
}