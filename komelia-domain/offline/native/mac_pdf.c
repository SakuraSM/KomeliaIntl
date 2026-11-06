#include <CoreFoundation/CoreFoundation.h>
#include <CoreGraphics/CoreGraphics.h>
#include <ImageIO/ImageIO.h>
#include <jni.h>
#include <limits.h>
#include <math.h>
#include <stdlib.h>

static void fail(JNIEnv *env, const char *message) {
    jclass type = (*env)->FindClass(env, "java/io/IOException");
    if (type != NULL) (*env)->ThrowNew(env, type, message);
}

static CGPDFDocumentRef open_pdf(JNIEnv *env, jstring path) {
    const jchar *characters = (*env)->GetStringChars(env, path, NULL);
    if (characters == NULL) return NULL;
    CFStringRef name = CFStringCreateWithCharacters(kCFAllocatorDefault, characters, (*env)->GetStringLength(env, path));
    (*env)->ReleaseStringChars(env, path, characters);
    if (name == NULL) { fail(env, "Unable to open PDF"); return NULL; }
    CFURLRef url = CFURLCreateWithFileSystemPath(kCFAllocatorDefault, name, kCFURLPOSIXPathStyle, false);
    CFRelease(name);
    if (url == NULL) { fail(env, "Unable to open PDF"); return NULL; }
    CGPDFDocumentRef document = CGPDFDocumentCreateWithURL(url);
    CFRelease(url);
    if (document == NULL) { fail(env, "Unable to open PDF"); return NULL; }
    if (!CGPDFDocumentIsUnlocked(document) && !CGPDFDocumentUnlockWithPassword(document, "")) {
        CGPDFDocumentRelease(document);
        fail(env, "PDF is password protected");
        return NULL;
    }
    return document;
}

static bool page_size(CGPDFPageRef page, jint *width, jint *height) {
    CGRect box = CGPDFPageGetBoxRect(page, kCGPDFCropBox);
    if (!isfinite(box.size.width) || !isfinite(box.size.height) || box.size.width <= 0 ||
        box.size.height <= 0 || box.size.width > INT_MAX || box.size.height > INT_MAX) return false;
    int rotation = ((CGPDFPageGetRotationAngle(page) % 360) + 360) % 360;
    bool rotated = rotation == 90 || rotation == 270;
    *width = (jint) fmax(1, round(rotated ? box.size.height : box.size.width));
    *height = (jint) fmax(1, round(rotated ? box.size.width : box.size.height));
    return true;
}

JNIEXPORT jintArray JNICALL Java_snd_komelia_offline_mediacontainer_MacPdfNative_pageSizes(
    JNIEnv *env, jobject self, jstring path
) {
    (void) self;
    CGPDFDocumentRef document = open_pdf(env, path);
    if (document == NULL) return NULL;
    size_t count = CGPDFDocumentGetNumberOfPages(document);
    if (count == 0 || count > INT_MAX / 2) {
        CGPDFDocumentRelease(document);
        fail(env, "PDF contains no readable pages");
        return NULL;
    }
    jintArray result = (*env)->NewIntArray(env, (jsize) (count * 2));
    if (result == NULL) { CGPDFDocumentRelease(document); return NULL; }
    jint *sizes = calloc(count * 2, sizeof(jint));
    if (sizes == NULL) { CGPDFDocumentRelease(document); fail(env, "Unable to allocate PDF page metadata"); return NULL; }
    for (size_t i = 0; i < count; i++) {
        CGPDFPageRef page = CGPDFDocumentGetPage(document, i + 1);
        if (page == NULL || !page_size(page, &sizes[i * 2], &sizes[i * 2 + 1])) {
            free(sizes);
            CGPDFDocumentRelease(document);
            fail(env, "Invalid PDF page size");
            return NULL;
        }
    }
    (*env)->SetIntArrayRegion(env, result, 0, (jsize) (count * 2), sizes);
    free(sizes);
    CGPDFDocumentRelease(document);
    return result;
}

JNIEXPORT jintArray JNICALL Java_snd_komelia_offline_mediacontainer_MacPdfNative_pageSize(
    JNIEnv *env, jobject self, jstring path, jint page_number
) {
    (void) self;
    CGPDFDocumentRef document = open_pdf(env, path);
    if (document == NULL) return NULL;
    CGPDFPageRef page = page_number > 0 && (size_t) page_number <= CGPDFDocumentGetNumberOfPages(document)
        ? CGPDFDocumentGetPage(document, (size_t) page_number) : NULL;
    jint dimensions[2];
    if (page == NULL) {
        CGPDFDocumentRelease(document);
        jclass type = (*env)->FindClass(env, "java/lang/IndexOutOfBoundsException");
        if (type != NULL) (*env)->ThrowNew(env, type, "PDF page does not exist");
        return NULL;
    }
    if (!page_size(page, &dimensions[0], &dimensions[1])) {
        CGPDFDocumentRelease(document);
        fail(env, "Invalid PDF page size");
        return NULL;
    }
    jintArray result = (*env)->NewIntArray(env, 2);
    if (result != NULL) (*env)->SetIntArrayRegion(env, result, 0, 2, dimensions);
    CGPDFDocumentRelease(document);
    return result;
}

JNIEXPORT jbyteArray JNICALL Java_snd_komelia_offline_mediacontainer_MacPdfNative_renderPage(
    JNIEnv *env, jobject self, jstring path, jint page_number, jint width, jint height
) {
    (void) self;
    if (page_number < 1 || width < 1 || height < 1 || width > 4096 || height > 4096) {
        fail(env, "Invalid PDF page or output size");
        return NULL;
    }
    CGPDFDocumentRef document = open_pdf(env, path);
    if (document == NULL) return NULL;
    CGPDFPageRef page = (size_t) page_number <= CGPDFDocumentGetNumberOfPages(document)
        ? CGPDFDocumentGetPage(document, (size_t) page_number) : NULL;
    if (page == NULL) { CGPDFDocumentRelease(document); fail(env, "PDF page does not exist"); return NULL; }
    CGColorSpaceRef color = CGColorSpaceCreateDeviceRGB();
    CGContextRef context = color == NULL ? NULL : CGBitmapContextCreate(NULL, (size_t) width, (size_t) height,
        8, (size_t) width * 4, color, kCGImageAlphaPremultipliedLast);
    if (color != NULL) CGColorSpaceRelease(color);
    if (context == NULL) { CGPDFDocumentRelease(document); fail(env, "Unable to allocate PDF page image"); return NULL; }
    CGRect target = CGRectMake(0, 0, width, height);
    CGContextSetRGBFillColor(context, 1, 1, 1, 1);
    CGContextFillRect(context, target);
    CGContextConcatCTM(context, CGPDFPageGetDrawingTransform(page, kCGPDFCropBox, target, 0, false));
    CGContextClipToRect(context, CGPDFPageGetBoxRect(page, kCGPDFCropBox));
    CGContextDrawPDFPage(context, page);
    CGImageRef image = CGBitmapContextCreateImage(context);
    CGContextRelease(context);
    CGPDFDocumentRelease(document);
    if (image == NULL) { fail(env, "Unable to render PDF page"); return NULL; }
    CFMutableDataRef data = CFDataCreateMutable(kCFAllocatorDefault, 0);
    CGImageDestinationRef destination = data == NULL ? NULL : CGImageDestinationCreateWithData(data, CFSTR("public.jpeg"), 1, NULL);
    if (destination == NULL) {
        if (data != NULL) CFRelease(data);
        CGImageRelease(image);
        fail(env, "Unable to encode PDF page");
        return NULL;
    }
    double quality = 0.95;
    CFNumberRef quality_number = CFNumberCreate(kCFAllocatorDefault, kCFNumberDoubleType, &quality);
    const void *keys[] = { kCGImageDestinationLossyCompressionQuality };
    const void *values[] = { quality_number };
    CFDictionaryRef options = quality_number == NULL ? NULL : CFDictionaryCreate(kCFAllocatorDefault, keys, values, 1,
        &kCFTypeDictionaryKeyCallBacks, &kCFTypeDictionaryValueCallBacks);
    CGImageDestinationAddImage(destination, image, options);
    bool encoded = CGImageDestinationFinalize(destination);
    if (options != NULL) CFRelease(options);
    if (quality_number != NULL) CFRelease(quality_number);
    CFRelease(destination);
    CGImageRelease(image);
    CFIndex length = CFDataGetLength(data);
    if (!encoded || length > INT_MAX) { CFRelease(data); fail(env, "Unable to encode PDF page"); return NULL; }
    jbyteArray result = (*env)->NewByteArray(env, (jsize) length);
    if (result != NULL) (*env)->SetByteArrayRegion(env, result, 0, (jsize) length, (const jbyte *) CFDataGetBytePtr(data));
    CFRelease(data);
    return result;
}
