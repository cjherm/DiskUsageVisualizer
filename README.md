# Disk Usage Visualizer

A small JavaFX desktop app that scans a directory and displays disk usage as an interactive sunburst chart.

## Features

- Scan any folder or drive and visualize file/folder sizes as a sunburst chart
- Configurable scan depth (1–10 sub-levels)
- Shows free space when scanning a volume root
- Back/forward navigation between previous scans
- Cancel a scan in progress
- Reports file count and scan duration

## Requirements

- Java 17+

## Running

```bash
./gradlew run
```

## Building a Windows executable

```bash
./gradlew jpackage
```

This produces a double-clickable `.exe` with a bundled runtime under `build/jpackage/DiskUsageVisualizer`.

## Tech stack

Java 17, JavaFX 21
