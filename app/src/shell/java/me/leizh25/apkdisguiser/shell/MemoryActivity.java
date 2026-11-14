package me.leizh25.apkdisguiser.shell;

import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.Gravity;

public final class MemoryActivity extends UninstallActivity {
    private static final String[][] EMOJI_GROUPS = {
        {"😀", "😂", "😍", "😎", "😴", "😮"},
        {"🐶", "🐱", "🐭", "🐰", "🐼", "🐸"},
        {"🌻", "🌵", "🌷", "🌴", "🍀", "🍁"},
        {"👍", "✌️", "👏", "👋", "👌", "🙌"},
        {"❤️", "💛", "💚", "💙", "💜", "💔"},
        {"🚗", "🚕", "🚲", "🚀", "✈️", "🚢"}
    };
    private String[] symbols = new String[EMOJI_GROUPS.length];
    private final int[] cards = new int[12];
    private final boolean[] matched = new boolean[12];
    private final Button[] buttons = new Button[12];
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private int first = -1;
    private int second = -1;
    private int moves;
    private TextView status;
    private final Runnable conceal = () -> {
        first = second = -1;
        render();
    };

    @Override
    protected void buildGame(LinearLayout content, Bundle state) {
        TextView title = new TextView(this);
        title.setText("记忆翻牌");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        content.addView(title);
        status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        content.addView(status);
        for (int row = 0; row < 3; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER);
            content.addView(line, new LinearLayout.LayoutParams(-2, -2));
            for (int column = 0; column < 4; column++) {
                int index = row * 4 + column;
                Button card = new Button(this);
                buttons[index] = card;
                card.setTextSize(22);
                card.setOnClickListener(view -> flip(index));
                line.addView(card, new LinearLayout.LayoutParams(dp(60), dp(64)));
            }
        }
        Button reset = new Button(this);
        reset.setText("重新开始");
        reset.setOnClickListener(view -> reset());
        content.addView(reset);
        reset();
        if (state != null && state.containsKey("cards")) {
            System.arraycopy(state.getIntArray("cards"), 0, cards, 0, cards.length);
            System.arraycopy(state.getBooleanArray("matched"), 0, matched, 0, matched.length);
            String[] savedSymbols = state.getStringArray("symbols");
            if (savedSymbols != null) symbols = savedSymbols;
            moves = state.getInt("moves");
            render();
        }
    }

    private void reset() {
        handler.removeCallbacks(conceal);
        first = second = -1;
        moves = 0;
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        for (int group = 0; group < EMOJI_GROUPS.length; group++) {
            String[] candidates = EMOJI_GROUPS[group];
            symbols[group] = candidates[random.nextInt(candidates.length)];
        }
        java.util.Arrays.fill(matched, false);
        for (int i = 0; i < cards.length; i++) cards[i] = i / 2;
        for (int i = cards.length - 1; i > 0; i--) {
            int j = java.util.concurrent.ThreadLocalRandom.current().nextInt(i + 1);
            int value = cards[i];
            cards[i] = cards[j];
            cards[j] = value;
        }
        render();
    }

    private void flip(int index) {
        if (second >= 0 || first == index || matched[index]) return;
        if (first < 0) {
            first = index;
        } else {
            moves++;
            if (cards[first] == cards[index]) {
                matched[first] = matched[index] = true;
                first = -1;
            } else {
                second = index;
                handler.postDelayed(conceal, 750);
            }
        }
        render();
    }

    private void render() {
        int pairs = 0;
        for (int i = 0; i < cards.length; i++) {
            if (matched[i]) pairs++;
            boolean visible = matched[i] || first == i || second == i;
            buttons[i].setText(visible ? symbols[cards[i]] : "?");
            buttons[i].setEnabled(!matched[i] && second < 0 && first != i);
        }
        status.setText((pairs == cards.length ? "配对完成" : "找到相同的两张牌")
            + " · " + pairs / 2 + "/6 对 · " + moves + " 步");
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putIntArray("cards", cards.clone());
        state.putBooleanArray("matched", matched.clone());
        state.putInt("moves", moves);
        state.putStringArray("symbols", symbols);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(conceal);
        super.onDestroy();
    }
}
