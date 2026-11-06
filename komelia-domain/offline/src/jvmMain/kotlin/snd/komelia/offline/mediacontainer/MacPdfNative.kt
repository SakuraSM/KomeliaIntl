package snd.komelia.offline.mediacontainer

import snd.jni.SharedLibrariesLoader

internal object MacPdfNative {
    init { SharedLibrariesLoader.loadMacLibrary("komelia_pdf") }

    external fun pageSizes(path: String): IntArray
    external fun pageSize(path: String, pageNumber: Int): IntArray
    external fun renderPage(path: String, pageNumber: Int, width: Int, height: Int): ByteArray
}
