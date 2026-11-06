#include <jni.h>
#include <png.h>
#include <stdlib.h>
#include <string.h>

JNIEXPORT jbyteArray JNICALL
Java_snd_komelia_AndroidOnnxRuntimeRecoveryTest_decodePngDirect(
    JNIEnv *env, jclass type, jbyteArray input) {
    (void)type;
    jbyte *encoded = (*env)->GetByteArrayElements(env, input, NULL);
    if (encoded == NULL) return NULL;

    png_image image;
    memset(&image, 0, sizeof(image));
    image.version = PNG_IMAGE_VERSION;
    void *pixels = NULL;
    jbyteArray result = NULL;
    if (!png_image_begin_read_from_memory(&image, encoded, (*env)->GetArrayLength(env, input))) goto failure;
    image.format = PNG_FORMAT_RGBA;
    if (image.width != 16 || image.height != 16) goto failure;
    pixels = malloc(PNG_IMAGE_SIZE(image));
    if (pixels == NULL || !png_image_finish_read(&image, NULL, pixels, 0, NULL)) goto failure;
    result = (*env)->NewByteArray(env, PNG_IMAGE_SIZE(image));
    if (result != NULL) (*env)->SetByteArrayRegion(env, result, 0, PNG_IMAGE_SIZE(image), pixels);
    goto done;

failure:
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalStateException"),
        image.message[0] ? image.message : "direct PNG probe failed");
done:
    free(pixels);
    png_image_free(&image);
    (*env)->ReleaseByteArrayElements(env, input, encoded, JNI_ABORT);
    return result;
}
