package com.umang96.backupsecurity.ftputil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;

public abstract class FsFileSystemView<T extends FsFile<X>, X> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected abstract T createFile(File file);

    protected abstract String absolute(String file);

    public T getFile(String file) {
        logger.trace("getFile({})", file);
        String abs = absolute(file);
        logger.trace("  getFile(abs: {})", file);
        return createFile(new File(abs));
    }
}
