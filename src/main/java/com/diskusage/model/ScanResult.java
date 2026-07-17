package com.diskusage.model;

import java.nio.file.Path;

/**
 * A completed scan, kept around so the user can navigate back/forward
 * through previously viewed charts without rescanning.
 */
public record ScanResult(Path path, DirNode root, long freeSpace, int maxDepth, long durationMillis) {
}
