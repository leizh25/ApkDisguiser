package me.leizh25.apkdisguiser.shell;

import android.os.Bundle;
import android.view.Gravity;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayDeque;
import java.util.concurrent.ThreadLocalRandom;

public final class SnakeActivity extends UninstallActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<Integer> snake = new ArrayDeque<>();
    private final TextView[] cells = new TextView[144];
    private TextView status;
    private int direction, next, food;
    private boolean running;
    private final Runnable tick = this::advance;

    @Override protected void buildGame(LinearLayout content, Bundle state) {
        status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        content.addView(status);
        int size = Math.min(dp(24), (getResources().getDisplayMetrics().widthPixels - dp(40)) / 12);
        for (int y = 0; y < 12; y++) {
            LinearLayout row = new LinearLayout(this);
            content.addView(row, new LinearLayout.LayoutParams(-2, -2));
            for (int x = 0; x < 12; x++) {
                TextView cell = new TextView(this);
                cells[y * 12 + x] = cell;
                row.addView(cell, new LinearLayout.LayoutParams(size, size));
            }
        }
        int[] directions = {3, 2, 0, 1};
        addDirectionPad(content, key -> {
            int turn = directions[key];
            if ((turn + 2) % 4 != direction) next = turn;
        });
        Button start = new Button(this);
        start.setText("开始 / 重新开始");
        start.setOnClickListener(v -> {
            reset();
            running = true;
            render("贪吃蛇");
            handler.postDelayed(tick, 300);
        });
        content.addView(start);
        reset();
    }

    private void reset() {
        handler.removeCallbacks(tick);
        running = false;
        snake.clear();
        snake.add(66);
        snake.add(65);
        snake.add(64);
        direction = next = 0;
        placeFood();
        render("贪吃蛇 · 点击开始");
    }

    private void placeFood() {
        int[] empty = new int[144];
        int count = 0;
        for (int i = 0; i < 144; i++) if (!snake.contains(i)) empty[count++] = i;
        food = count == 0 ? -1 : empty[ThreadLocalRandom.current().nextInt(count)];
    }

    private void advance() {
        direction = next;
        int head = snake.getFirst();
        int x = head % 12 + (direction == 0 ? 1 : direction == 2 ? -1 : 0);
        int y = head / 12 + (direction == 1 ? 1 : direction == 3 ? -1 : 0);
        int target = y * 12 + x;
        if (x < 0 || x >= 12 || y < 0 || y >= 12 ||
                (snake.contains(target) && (target != snake.getLast() || target == food))) {
            running = false;
            render("游戏结束");
            return;
        }
        if (target != food) snake.removeLast();
        snake.addFirst(target);
        if (target == food) placeFood();
        running = food >= 0;
        render(running ? "贪吃蛇" : "挑战成功");
        if (running) handler.postDelayed(tick, 300);
    }

    private void render(String title) {
        status.setText(title + " · 得分 " + (snake.size() - 3));
        for (int i = 0; i < 144; i++) {
            cells[i].setBackgroundColor(i == food ? 0xffc62828 : snake.contains(i) ? 0xff00665a
                : (i / 12 + i % 12) % 2 == 0 ? 0xffeeebf8 : 0xffe3def1);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (running) handler.postDelayed(tick, 300);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }
}
