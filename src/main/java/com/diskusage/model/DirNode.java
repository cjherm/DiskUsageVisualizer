package com.diskusage.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A single node in the scanned filesystem tree: either a file (no children)
 * or a directory (children are its immediate entries, possibly rolled up
 * beyond the configured max depth).
 */
public class DirNode {

    private final String name;
    private final Path path;
    private final boolean directory;
    private long size;
    private long fileCount;
    private final List<DirNode> children = new ArrayList<>();

    public DirNode(Path path, boolean directory) {
        this.path = path;
        this.directory = directory;
        Path fileName = path.getFileName();
        this.name = fileName != null ? fileName.toString() : path.toString();
    }

    public void addChild(DirNode child) {
        children.add(child);
    }

    public String getName() {
        return name;
    }

    public Path getPath() {
        return path;
    }

    public boolean isDirectory() {
        return directory;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public void addSize(long delta) {
        this.size += delta;
    }

    public long getFileCount() {
        return fileCount;
    }

    public void setFileCount(long fileCount) {
        this.fileCount = fileCount;
    }

    public void addFileCount(long delta) {
        this.fileCount += delta;
    }

    public List<DirNode> getChildren() {
        return children;
    }
}
