package org.janelia.it.workstation.cache.large_volume.stack;

import org.janelia.it.workstation.gui.large_volume_viewer.AbstractTextureLoadAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * Created by murphys on 10/23/2015.
 */
public class SimpleFileCache {

    private static Logger log = LoggerFactory.getLogger(SimpleFileCache.class);

    private int cacheSize=0;
    private int fileSize=0;

    Map<File, ByteBuffer> cache = Collections.synchronizedMap(new LinkedHashMap<File, ByteBuffer>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<File,ByteBuffer> eldest) {
            if (cache.size()>cacheSize) {
                return true;
            }
            return false;
        }
    });

    public SimpleFileCache(int cacheSize, int fileSize) {
        this.cacheSize=cacheSize;
        this.fileSize=fileSize;
    }

    public ByteBuffer getBytes(File file) {
        return cache.get(file);
    }

    public void putBytes(File file, ByteBuffer data) throws AbstractTextureLoadAdapter.TileLoadError {
        if (data.capacity()==fileSize) {
            cache.put(file, data);
        } else {
            throw new AbstractTextureLoadAdapter.TileLoadError("ByteBuffer cache entry must match fileSize="+fileSize);
        }
    }

    public int size() {
        return cache.size();
    }

    public void limitToNeighborhood(List<File> neighborhoodList) {
        Set<File> removeSet=new HashSet<>();
        for (File f : cache.keySet()) {
            if (!neighborhoodList.contains(f))
                removeSet.add(f);
        }
        for (File f : removeSet) {
            log.info("cache removing "+f.getAbsolutePath());
            cache.remove(f);
        }
    }


}
