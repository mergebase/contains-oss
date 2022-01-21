package com.mergebase.strings;

import java.util.zip.ZipInputStream;

/**
 * An interface that allows us to re-read a ZipInputStream as many times as we want.
 */
public interface Zipper {
    ZipInputStream getFreshZipStream();

    void close();

    public long crc64();

}

