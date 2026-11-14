package me.leizh25.apkdisguiser.shell;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.ThreadLocalRandom;

public final class PuzzleActivity extends UninstallActivity {
    private final int[] tiles = new int[9];
    private final Button[] cells = new Button[9];
    private int blank, moves;
    private TextView status;

    @Override protected void buildGame(LinearLayout content, Bundle state) {
        status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        content.addView(status);
        for (int y = 0; y < 3; y++) {
            LinearLayout row = new LinearLayout(this);
            content.addView(row, new LinearLayout.LayoutParams(-2, -2));
            for (int x = 0; x < 3; x++) {
                int index = y * 3 + x;
                Button cell = new Button(this);
                cells[index] = cell;
                cell.setTextSize(24);
                row.addView(cell, new LinearLayout.LayoutParams(dp(72), dp(72)));
                cell.setOnClickListener(v -> {
                    if (!solved() && adjacent(index)) {
                        slide(index);
                        moves++;
                        render();
                    }
                });
            }
        }
        Button reset = new Button(this);
        reset.setText("重新开始");
        reset.setOnClickListener(v -> reset());
        content.addView(reset);
        reset();
    }

    private boolean adjacent(int index) {
        return Math.abs(index / 3 - blank / 3) + Math.abs(index % 3 - blank % 3) == 1;
    }

    private void slide(int index) {
        tiles[blank] = tiles[index];
        tiles[index] = 0;
        blank = index;
    }

    private boolean solved() {
        for (int i = 0; i < 8; i++) if (tiles[i] != i + 1) return false;
        return true;
    }

    private void reset() {
        for (int i = 0; i < 8; i++) tiles[i] = i + 1;
        blank = 8;
        tiles[blank] = 0;
        int previous = -1;
        int[] options = new int[4];
        for (int step = 0; step < 41; step++) {
            int count = 0;
            for (int i = 0; i < 9; i++) if (i != previous && adjacent(i)) options[count++] = i;
            previous = blank;
            slide(options[ThreadLocalRandom.current().nextInt(count)]);
        }
        moves = 0;
        render();
    }

    private void render() {
        status.setText((solved() ? "拼图完成" : "华容道 · 将数字排成 1～8") + " · " + moves + " 步");
        for (int i = 0; i < 9; i++) {
            cells[i].setText(tiles[i] == 0 ? "" : String.valueOf(tiles[i]));
            cells[i].setEnabled(!solved() && adjacent(i));
        }
    }
}
