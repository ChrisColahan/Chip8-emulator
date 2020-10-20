package com.incendiary;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class Main extends Application {

    private static final int SCALE = 8;
    private static final int ACTUAL_WIDTH = Chip8Emulator.VIRTUAL_WIDTH * SCALE;
    private static final int ACTUAL_HEIGHT = Chip8Emulator.VIRTUAL_HEIGHT * SCALE;

    private Chip8Emulator chip8Emulator = null;

    @Override
    public void start(Stage primaryStage) throws Exception {

        final FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Program");

        primaryStage.setTitle("Chip 8 Emulator");

        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(25, 25, 25, 25));

        Button chooseProgramBtn = new Button("Select Program");
        chooseProgramBtn.setAlignment(Pos.TOP_CENTER);
        chooseProgramBtn.setOnAction(event -> {
            File file = fileChooser.showOpenDialog(primaryStage);
            if(file != null) {
                startEmulator(file);
            }
        });
        grid.add(chooseProgramBtn, 0, 0);

        final Canvas canvas = new Canvas(ACTUAL_WIDTH, ACTUAL_HEIGHT);
        grid.add(canvas, 0, 1);

        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                GraphicsContext gc = canvas.getGraphicsContext2D();

                if(chip8Emulator == null) {
                    drawCheckerboard(gc);
                } else {
                    drawEmulatorState(gc);
                }
            }

        };

        Scene scene = new Scene(grid, 600, 600);

        scene.setOnKeyPressed(event -> chip8Emulator.keyInput(event.getCode(), true));
        scene.setOnKeyReleased(event -> chip8Emulator.keyInput(event.getCode(), false));

        primaryStage.setOnCloseRequest(event -> {
            try {
                chip8Emulator.stop();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        primaryStage.setScene(scene);
        primaryStage.show();

        timer.start();
    }

    private void drawCheckerboard(GraphicsContext gc) {
        boolean fill = true;

        for(int y = 0; y < ACTUAL_HEIGHT; y += SCALE) {
            for(int x = 0; x < ACTUAL_WIDTH; x += SCALE) {
                gc.setFill(fill ? Color.BLACK : Color.WHITE);
                gc.fillRect(x, y, SCALE, SCALE);
                fill = !fill;
            }
            fill = !fill;
        }
    }

    private void drawEmulatorState(GraphicsContext gc) {
        boolean[][] pixels = chip8Emulator.getScreenState();

        for(int y = 0; y < ACTUAL_HEIGHT; y += SCALE) {
            for(int x = 0; x < ACTUAL_WIDTH; x += SCALE) {
                gc.setFill(pixels[x / SCALE][y / SCALE] ? Color.WHITE : Color.BLACK);
                gc.fillRect(x, y, SCALE, SCALE);
            }
        }
    }

    private void startEmulator(File file) {
        System.out.println("Starting emulator with file " + file.getAbsolutePath());

        try (FileInputStream inputStream = new FileInputStream(file)) {
            if(this.chip8Emulator != null) {
                this.chip8Emulator.stop();
            }

            byte[] bytes = inputStream.readAllBytes();

            this.chip8Emulator = new Chip8Emulator(bytes);
            this.chip8Emulator.start();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
