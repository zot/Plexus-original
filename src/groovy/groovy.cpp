#include "pch.h"
#include "cube.h"
#include "iengine.h"
#include <jni.h>
#include <stdio.h>

#define PATH_SEPARATOR ':' /* define it to be ':' on Solaris */

static bool initted = false;
static JavaVM *jvm = NULL;
static jmethodID handler = NULL;
static jclass handlerClass;
static jclass stringClass;
static JNIEnv *env = NULL;

void destroygroovy() {
	if (jvm) {
	    if (env->ExceptionOccurred()) {
	    	env->ExceptionDescribe();
	    }
	    jvm->DestroyJavaVM();
	    jvm = NULL;
	}
}

COMMAND(destroygroovy, "");
void initgroovy(char *path, char *cl) {
	if (!initted) {
	    jint res;
	    jmethodID mid;
	    jobjectArray args;
	    char opt[1024];
	    JavaVMInitArgs vm_args;
	    JavaVMOption options[1];

	    sprintf(opt, "-Djava.class.path=%s", path);
	    printf("PATH: '%s', CLASS: '%s'\nBOO\n", opt, cl);
	    options[0].optionString = opt;
	    vm_args.version = 0x00010002;
	    vm_args.options = options;
	    vm_args.nOptions = 1;
	    vm_args.ignoreUnrecognized = JNI_TRUE;
	    /* Create the Java VM */
	    res = JNI_CreateJavaVM(&jvm, (void**)&env, &vm_args);

	    if (res < 0) {
	        fprintf(stderr, "Can't create Java VM\n");
	        exit(1);
	    }
	    handlerClass = env->FindClass(cl);
	    if (handlerClass == NULL) {
	        goto destroy;
	    }

	    mid = env->GetStaticMethodID(handlerClass, "main", "([Ljava/lang/String;)V");
	    if (mid == NULL) {
	        goto destroy;
	    }
	    handler = env->GetStaticMethodID(handlerClass, "handle", "([Ljava/lang/String;)Ljava/lang/String;");
	    if (handler == NULL) {
	    	goto destroy;
	    }
	    stringClass = env->FindClass("java/lang/String");
	    args = env->NewObjectArray(0, stringClass, 0);
	    if (args == NULL) {
	        goto destroy;
	    }
	    env->CallStaticVoidMethod(handlerClass, mid, args);
	    return;

	destroy:
		destroygroovy();
	}
}
COMMAND(initgroovy, "ss");

void groovy(char **inputArgs, int *numargs) {
	if (env) {
		jobject result;
		printf("creating string[%d]...\n", *numargs);
		jobjectArray args = env->NewObjectArray(*numargs, stringClass, 0);
		printf("done\n");

		if (args == NULL) {
			goto destroy;
		}
		for (int i = 0; i < *numargs; i++) {
			printf("assigning: %s\n", inputArgs[i]);
			jstring jstr = env->NewStringUTF(inputArgs[i]);

			if (jstr == NULL) {
				goto destroy;
			}
			env->SetObjectArrayElement(args, i, jstr);
		}
		result = env->CallStaticObjectMethod(handlerClass, handler, args);
		const char *chars;
		chars = env->GetStringUTFChars((jstring)result, NULL);
		printf("RESULT: %s\n", (char*)chars);
		executeret(chars);
		return;

		destroy:
			destroygroovy();
	}
}
COMMAND(groovy, "V");
