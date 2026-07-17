package com.diskusage;

import com.diskusage.chart.SunburstChart;
import com.diskusage.model.DirNode;
import com.diskusage.model.ScanResult;
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
import javafx.scene.control.Tooltip;
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
import java.util.ArrayList;
import java.util.List;

public class App extends Application {

    private final TextField pathField = new TextField();
    private final Spinner<Integer> depthSpinner = new Spinner<>(1, 10, 3);
    private final Button scanButton = new Button("Scan");
    private final Button cancelButton = new Button("Cancel");
    private final Button backButton = new Button("◀");
    private final Button forwardButton = new Button("▶");
    private final ProgressIndicator progressIndicator = new ProgressIndicator();
    private final Label statusLabel = new Label("Enter a path or click Select, then Scan.");
    private final SunburstChart chart = new SunburstChart();

    private final List<ScanResult> history = new ArrayList<>();
    private int historyIndex = -1;

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

        backButton.setTooltip(new Tooltip("Previous chart"));
        forwardButton.setTooltip(new Tooltip("Next chart"));
        backButton.setOnAction(e -> goBack());
        forwardButton.setOnAction(e -> goForward());
        updateHistoryButtons();

        HBox controls = new HBox(8,
                backButton, forwardButton,
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

        ScanResult result = new ScanResult(scannedPath, root, freeSpace, maxDepth);
        if (historyIndex < history.size() - 1) {
            // A new scan branches off the current point in history, discarding "forward" entries.
            history.subList(historyIndex + 1, history.size()).clear();
        }
        history.add(result);
        historyIndex = history.size() - 1;
        updateHistoryButtons();

        displayResult(result);
    }

    private void goBack() {
        if (historyIndex > 0) {
            historyIndex--;
            displayResult(history.get(historyIndex));
            updateHistoryButtons();
        }
    }

    private void goForward() {
        if (historyIndex < history.size() - 1) {
            historyIndex++;
            displayResult(history.get(historyIndex));
            updateHistoryButtons();
        }
    }

    private void updateHistoryButtons() {
        backButton.setDisable(historyIndex <= 0);
        forwardButton.setDisable(historyIndex >= history.size() - 1);
    }

    private void displayResult(ScanResult result) {
        pathField.setText(result.path().toString());
        chart.setData(result.root(), result.freeSpace(), result.maxDepth());

        StringBuilder sb = new StringBuilder();
        sb.append(result.path()).append("  -  used: ").append(FormatUtil.formatBytes(result.root().getSize()));
        if (result.freeSpace() > 0) {
            sb.append(", free: ").append(FormatUtil.formatBytes(result.freeSpace()));
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
