package me.leizh25.apkdisguiser.shell;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public final class MineActivity extends UninstallActivity {
    private final boolean[] mines = new boolean[64], open = new boolean[64], flags = new boolean[64];
    private final Button[] cells = new Button[64];
    private boolean started, lost;
    private TextView status;

    @Override protected void buildGame(LinearLayout content, Bundle state) {
        status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        content.addView(status);
        int size = Math.min(dp(42), (getResources().getDisplayMetrics().widthPixels - dp(40)) / 8);
        for (int y = 0; y < 8; y++) {
            LinearLayout row = new LinearLayout(this);
            content.addView(row, new LinearLayout.LayoutParams(-2, -2));
            for (int x = 0; x < 8; x++) {
                int i = y * 8 + x;
                Button cell = new Button(this);
                cells[i] = cell;
                cell.setPadding(0, 0, 0, 0);
                row.addView(cell, new LinearLayout.LayoutParams(size, size));
                cell.setOnClickListener(v -> reveal(i));
                cell.setOnLongClickListener(v -> {
                    if (!open[i] && !lost && remaining() > 0) flags[i] = !flags[i];
                    render();
                    return true;
                });
            }
        }
        Button reset = new Button(this);
        reset.setText("重新开始");
        reset.setOnClickListener(v -> {
            Arrays.fill(mines, false);
            Arrays.fill(open, false);
            Arrays.fill(flags, false);
            started = lost = false;
            render();
        });
        content.addView(reset);
        render();
    }

    private void reveal(int i) {
        if (lost || remaining() == 0 || flags[i]) return;
        if (!started) {
            started = true;
            for (int count = 0; count < 10;) {
                int index = ThreadLocalRandom.current().nextInt(64);
                if (index != i && !mines[index]) {
                    mines[index] = true;
                    count++;
                }
            }
        }
        lost = mines[i];
        if (!lost) flood(i);
        render();
    }

    private int adjacent(int i, int j) {
        return Math.max(Math.abs(i / 8 - j / 8), Math.abs(i % 8 - j % 8));
    }

    private int nearby(int i) {
        int count = 0;
        for (int j = 0; j < 64; j++) if (mines[j] && adjacent(i, j) == 1) count++;
        return count;
    }

    private void flood(int i) {
        if (open[i] || flags[i] || mines[i]) return;
        open[i] = true;
        if (nearby(i) == 0) {
            for (int j = 0; j < 64; j++) if (adjacent(i, j) == 1) flood(j);
        }
    }

    private int remaining() {
        int count = 54;
        for (boolean visible : open) if (visible) count--;
        return count;
    }

    private void render() {
        status.setText(lost ? "踩到雷了" : remaining() == 0 ? "排雷成功" : "扫雷 · 10 颗雷 · 长按插旗");
        for (int i = 0; i < 64; i++) {
            int count = open[i] ? nearby(i) : 0;
            cells[i].setText(lost && mines[i] ? "✹" : flags[i] ? "⚑" : count > 0 ? String.valueOf(count) : "");
            cells[i].setEnabled(!open[i] && !lost && remaining() > 0);
        }
    }
}
