package com.diskusage;

import com.diskusage.chart.SunburstChart;
import com.diskusage.model.DirNode;
import com.diskusage.scan.DirectoryScanTask;
import com.diskusage.util.FormatUtil;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class App extends Application {

    private final TextField pathField = new TextField();
    private final Spinner<Integer> depthSpinner = new Spinner<>(1, 10, 3);
    private final Button scanButton = new Button("Scan");
    private final Button cancelButton = new Button("Cancel");
    private final ProgressIndicator progressIndicator = new ProgressIndicator();
    private final Label statusLabel = new Label("Enter a path or click Select, then Scan.");
    private final SunburstChart chart = new SunburstChart();

    private DirectoryScanTask currentTask;

    @Override
    public void start(Stage stage) {
        pathField.setPromptText("e.g. C:\\Users or C:\\");
        pathField.setPrefWidth(280);
        pathField.setMinWidth(120);
        pathField.setOnAction(e -> startScan());
        HBox.setHgrow(pathField, Priority.ALWAYS);

        Button selectButton = new Button("Select...");
        selectButton.setOnAction(e -> onSelectDirectory(stage));

        depthSpinner.setEditable(true);
        depthSpinner.setPrefWidth(70);

        scanButton.setOnAction(e -> startScan());
        cancelButton.setOnAction(e -> cancelScan());
        cancelButton.setVisible(false);
        cancelButton.managedProperty().bind(cancelButton.visibleProperty());
        progressIndicator.setVisible(false);
        progressIndicator.managedProperty().bind(progressIndicator.visibleProperty());
        progressIndicator.setPrefSize(20, 20);

        Label pathLabel = new Label("Path:");
        Label depthLabel = new Label("Max sub-levels:");
        pathLabel.setMinWidth(Region.USE_PREF_SIZE);
        depthLabel.setMinWidth(Region.USE_PREF_SIZE);

        HBox controls = new HBox(8,
                pathLabel, pathField, selectButton,
                depthLabel, depthSpinner,
                scanButton, cancelButton, progressIndicator);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(10));

        StackPane chartHolder = new StackPane(chart);
        chartHolder.setPadding(new Insets(10));

        statusLabel.setPadding(new Insets(6, 10, 10, 10));

        BorderPane root = new BorderPane();
        root.setTop(controls);
        root.setCenter(chartHolder);
        root.setBottom(statusLabel);

        stage.setTitle("Disk Usage Visualizer");
        stage.setScene(new Scene(root, 780, 640));
        stage.show();
    }

    private void onSelectDirectory(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select a directory");
        File initial = new File(pathField.getText().trim());
        if (initial.isDirectory()) {
            chooser.setInitialDirectory(initial);
        }
        File selected = chooser.showDialog(stage);
        if (selected != null) {
            pathField.setText(selected.getAbsolutePath());
            startScan();
        }
    }

    private void startScan() {
        if (currentTask != null && currentTask.isRunning()) {
            return;
        }
        String text = pathField.getText().trim();
        if (text.isEmpty()) {
            statusLabel.setText("Please enter or select a path first.");
            return;
        }
        Path path;
        try {
            path = Paths.get(text);
        } catch (Exception e) {
            statusLabel.setText("Invalid path: " + text);
            return;
        }
        if (!Files.isDirectory(path)) {
            statusLabel.setText("Not a directory: " + path);
            return;
        }

        int maxDepth = depthSpinner.getValue();
        DirectoryScanTask task = new DirectoryScanTask(path, maxDepth);
        currentTask = task;

        statusLabel.textProperty().bind(task.messageProperty());
        progressIndicator.setVisible(true);
        cancelButton.setVisible(true);
        scanButton.setDisable(true);
        chart.setBusy(true);

        task.setOnSucceeded(e -> {
            statusLabel.textProperty().unbind();
            chart.setBusy(false);
            onScanFinished(task.getValue(), path, maxDepth);
        });
        task.setOnFailed(e -> {
            statusLabel.textProperty().unbind();
            progressIndicator.setVisible(false);
            cancelButton.setVisible(false);
            scanButton.setDisable(false);
            chart.setBusy(false);
            Throwable ex = task.getException();
            showError("Scan failed: " + (ex != null ? ex.getMessage() : "unknown error"));
        });
        task.setOnCancelled(e -> {
            statusLabel.textProperty().unbind();
            statusLabel.setText("Scan cancelled.");
            progressIndicator.setVisible(false);
            cancelButton.setVisible(false);
            scanButton.setDisable(false);
            chart.setBusy(false);
        });

        Thread thread = new Thread(task, "dir-scan");
        thread.setDaemon(true);
        thread.start();
    }

    private void cancelScan() {
        if (currentTask != null) {
            currentTask.cancel();
        }
    }

    private void onScanFinished(DirNode root, Path scannedPath, int maxDepth) {
        progressIndicator.setVisible(false);
        cancelButton.setVisible(false);
        scanButton.setDisable(false);

        long freeSpace = 0;
        if (isVolumeRoot(scannedPath)) {
            try {
                FileStore store = Files.getFileStore(scannedPath);
                freeSpace = store.getUsableSpace();
            } catch (IOException e) {
                // Free space unavailable; proceed without it.
            }
        }

        chart.setData(root, freeSpace, maxDepth);

        StringBuilder sb = new StringBuilder();
        sb.append(scannedPath).append("  -  used: ").append(FormatUtil.formatBytes(root.getSize()));
        if (freeSpace > 0) {
            sb.append(", free: ").append(FormatUtil.formatBytes(freeSpace));
        }
        statusLabel.setText(sb.toString());
    }

    private boolean isVolumeRoot(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        return absolute.getParent() == null;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
