package me.leizh25.apkdisguiser.shell;

import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public final class MergeActivity extends UninstallActivity {
    private final int[] tiles = new int[16];
    private final TextView[] views = new TextView[16];
    private TextView status;
    private long points;
    private float startX;
    private float startY;

    @Override
    protected void buildGame(LinearLayout content, Bundle state) {
        TextView title = new TextView(this);
        title.setText("2048");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        content.addView(title);
        status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        content.addView(status);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        content.addView(grid, new LinearLayout.LayoutParams(-2, -2));
        for (int row = 0; row < 4; row++) {
            LinearLayout line = new LinearLayout(this);
            grid.addView(line);
            for (int col = 0; col < 4; col++) {
                TextView tile = new TextView(this);
                tile.setGravity(Gravity.CENTER);
                tile.setTextSize(19);
                views[row * 4 + col] = tile;
                LinearLayout.LayoutParams square = new LinearLayout.LayoutParams(dp(57), dp(57));
                square.setMargins(dp(2), dp(2), dp(2), dp(2));
                line.addView(tile, square);
            }
        }
        grid.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                startX = event.getX();
                startY = event.getY();
                view.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                float x = event.getX() - startX;
                float y = event.getY() - startY;
                if (Math.max(Math.abs(x), Math.abs(y)) >= dp(20)) {
                    move(Math.abs(x) >= Math.abs(y) ? (x < 0 ? 0 : 1) : (y < 0 ? 2 : 3));
                }
                view.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return true;
        });
        int[] directions = {2, 0, 1, 3};
        addDirectionPad(content, key -> move(directions[key]));
        Button reset = new Button(this);
        reset.setText("重新开始");
        reset.setOnClickListener(view -> reset());
        content.addView(reset);
        if (state != null && state.containsKey("tiles")) {
            System.arraycopy(state.getIntArray("tiles"), 0, tiles, 0, tiles.length);
            points = state.getLong("points");
            render();
        } else {
            reset();
        }
    }

    private void reset() {
        Arrays.fill(tiles, 0);
        points = 0;
        spawn();
        spawn();
        render();
    }

    private void spawn() {
        int[] empty = new int[16];
        int count = 0;
        for (int i = 0; i < tiles.length; i++) if (tiles[i] == 0) empty[count++] = i;
        if (count == 0) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        tiles[empty[random.nextInt(count)]] = random.nextInt(10) == 0 ? 4 : 2;
    }

    private int index(int direction, int line, int offset) {
        switch (direction) {
            case 0: return line * 4 + offset;
            case 1: return line * 4 + 3 - offset;
            case 2: return offset * 4 + line;
            default: return (3 - offset) * 4 + line;
        }
    }

    private void move(int direction) {
        int[] before = tiles.clone();
        for (int line = 0; line < 4; line++) {
            int[] values = new int[4];
            int count = 0;
            for (int i = 0; i < 4; i++) {
                int value = tiles[index(direction, line, i)];
                if (value != 0) values[count++] = value;
            }
            int written = 0;
            for (int i = 0; i < count; i++) {
                int value = values[i];
                if (i + 1 < count && values[i + 1] == value) {
                    value *= 2;
                    points += value;
                    i++;
                }
                tiles[index(direction, line, written++)] = value;
            }
            while (written < 4) tiles[index(direction, line, written++)] = 0;
        }
        if (!Arrays.equals(before, tiles)) spawn();
        render();
    }

    private void render() {
        boolean playable = false;
        boolean reachedGoal = false;
        for (int i = 0; i < tiles.length; i++) {
            int value = tiles[i];
            views[i].setText(value == 0 ? "" : String.valueOf(value));
            android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
            background.setCornerRadius(dp(10));
            background.setColor(value == 0 ? 0xffeeebf8 : value >= 128 ? 0xffbcb0ff : 0xffdcd4ff);
            views[i].setBackground(background);
            views[i].setTextColor(0xff24204c);
            playable |= value == 0 || (i % 4 != 3 && value == tiles[i + 1])
                || (i < 12 && value == tiles[i + 4]);
            reachedGoal |= value >= 2048;
        }
        status.setText("得分 " + points + (playable ? reachedGoal ? " · 已达到 2048" : " · 滑动合并" : " · 游戏结束"));
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putIntArray("tiles", tiles.clone());
        state.putLong("points", points);
    }
}
