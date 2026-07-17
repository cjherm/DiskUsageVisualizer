package com.diskusage.scan;

import com.diskusage.model.DirNode;
import javafx.concurrent.Task;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Recursively scans a directory tree in the background, building a {@link DirNode}
 * tree down to a configured max depth. Beyond that depth, subtree sizes are still
 * fully aggregated (just not expanded into individual nodes) so ring proportions
 * stay accurate. Symlinks/junctions are not followed, to avoid cycles.
 */
public class DirectoryScanTask extends Task<DirNode> {

    private final Path rootPath;
    private final int maxDepth;

    public DirectoryScanTask(Path rootPath, int maxDepth) {
        this.rootPath = rootPath;
        this.maxDepth = maxDepth;
    }

    @Override
    protected DirNode call() {
        updateMessage("Scanning " + rootPath);
        return scan(rootPath, 0);
    }

    private DirNode scan(Path path, int depth) {
        boolean isDirectory = Files.isDirectory(path) && !Files.isSymbolicLink(path);
        DirNode node = new DirNode(path, isDirectory);

        if (isCancelled()) {
            return node;
        }

        if (!isDirectory) {
            node.setSize(sizeOfFile(path));
            node.setFileCount(1);
            return node;
        }

        if (depth >= maxDepth) {
            long[] sizeAndCount = computeSizeAndCount(path);
            node.setSize(sizeAndCount[0]);
            node.setFileCount(sizeAndCount[1]);
            return node;
        }

        updateMessage("Scanning " + path);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (Path entry : stream) {
                if (isCancelled()) {
                    break;
                }
                DirNode child = scan(entry, depth + 1);
                node.addChild(child);
                node.addSize(child.getSize());
                node.addFileCount(child.getFileCount());
            }
        } catch (IOException e) {
            // Directory not listable (permissions, etc.) - leave node with size 0.
        }
        return node;
    }

    private long sizeOfFile(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }

    /**
     * Sums size and counts files below {@code dir} in a single walk, since both
     * numbers come from the same {@link BasicFileAttributes} already being read
     * per file - counting adds no extra I/O over just summing size.
     */
    private long[] computeSizeAndCount(Path dir) {
        long[] totals = {0L, 0L};
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (isCancelled()) {
                        return FileVisitResult.TERMINATE;
                    }
                    totals[0] += attrs.size();
                    totals[1]++;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path subDir, BasicFileAttributes attrs) {
                    if (isCancelled()) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (!subDir.equals(dir) && Files.isSymbolicLink(subDir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // Best-effort; partial totals are used.
        }
        return totals;
    }
}
