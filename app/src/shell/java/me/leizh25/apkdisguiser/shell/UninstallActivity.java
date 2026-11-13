package me.leizh25.apkdisguiser.shell;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import java.util.concurrent.ThreadLocalRandom;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;


public final class UninstallActivity extends Activity {
    private static final long STARTED_AT = SystemClock.elapsedRealtime();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView runtime;
    private long computerDue;
    private boolean resultAcknowledged;
    private AlertDialog resultDialog;
    private final Runnable clock = this::updateRuntime;
    private final Runnable computer = this::computerMove;
    private final int[] board = new int[9];
    private final Button[] cells = new Button[9];
    private TextView status;
    private static final int[][] LINES = {
        {0,1,2}, {3,4,5}, {6,7,8}, {0,3,6}, {1,4,7}, {2,5,8}, {0,4,8}, {2,4,6}
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle(getApplicationInfo().loadLabel(getPackageManager()));
        if (getActionBar() != null) {
            getActionBar().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xff1976d2));
            getWindow().setStatusBarColor(0xff1565c0);
        }
        if (state != null) {
            int[] saved = state.getIntArray("board");
            if (saved != null) System.arraycopy(saved, 0, board, 0, board.length);
            computerDue = state.getLong("computerDue");
            resultAcknowledged = state.getBoolean("resultAcknowledged");
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(20), dp(16), dp(20), dp(16));
        scroll.addView(content);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setBackgroundColor(0xfff8f7ff);


        content.setBackgroundColor(0xfff8f7ff);
        runtime = new TextView(this);
        runtime.setGravity(Gravity.END);
        runtime.setTextSize(12);
        runtime.setTextColor(0xff494567);
        content.addView(runtime, new LinearLayout.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("井字棋");
        title.setTextSize(28);
        title.setTextColor(0xff24204c);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(20), 0, 0);
        content.addView(title);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        status = new TextView(this);
        status.setTextColor(0xff494567);
        status.setPadding(0, dp(6), 0, dp(12));
        status.setGravity(Gravity.CENTER);
        content.addView(status, new LinearLayout.LayoutParams(-1, -2));

        int size = Math.min(dp(72), Math.max(dp(32),
            (getResources().getDisplayMetrics().widthPixels - dp(64)) / 3));
        for (int row = 0; row < 3; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER);
            content.addView(line, new LinearLayout.LayoutParams(-1, -2));
            for (int column = 0; column < 3; column++) {
                final int index = row * 3 + column;
                Button cell = new Button(this);
                cell.setTextSize(30);
                cell.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                cell.setPadding(0, 0, 0, 0);
                cell.setOnClickListener(view -> play(index));
                cells[index] = cell;
                LinearLayout.LayoutParams square = new LinearLayout.LayoutParams(size, size);
                square.setMargins(dp(4), dp(4), dp(4), dp(4));
                line.addView(cell, square);
            }
        }
        Button restart = new Button(this);
        restart.setText("重新开始");
        restart.setOnClickListener(view -> restartGame());
        content.addView(restart);

        setContentView(page);
        renderBoard();
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        TextView uninstall = new TextView(this);
        uninstall.setText("卸载");
        uninstall.setTextColor(0xffff5252);
        uninstall.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        uninstall.setTextSize(16);
        uninstall.setGravity(Gravity.CENTER);
        uninstall.setMinHeight(dp(48));
        uninstall.setPadding(dp(16), 0, dp(16), 0);
        uninstall.setOnClickListener(view -> startActivity(new Intent(
            Intent.ACTION_DELETE, Uri.fromParts("package", getPackageName(), null))));
        menu.add("卸载").setActionView(uninstall)
            .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putIntArray("board", board.clone());
        state.putLong("computerDue", computerDue);
        state.putBoolean("resultAcknowledged", resultAcknowledged);
        super.onSaveInstanceState(state);
    }

    private void restartGame() {
        handler.removeCallbacks(computer);
        computerDue = 0;
        resultAcknowledged = false;
        java.util.Arrays.fill(board, 0);
        renderBoard();
    }

    private void showResult() {
        int result = outcome();
        if (result == 0 || resultAcknowledged || resultDialog != null) return;
        resultDialog = new AlertDialog.Builder(this)
            .setTitle("对局结束")
            .setMessage(result == 1 ? "你赢了" : result == 2 ? "电脑赢了" : "平局")
            .setNegativeButton("关闭", (dialog, which) -> resultAcknowledged = true)
            .setPositiveButton("再来一局", (dialog, which) -> restartGame())
            .setOnCancelListener(dialog -> resultAcknowledged = true)
            .setOnDismissListener(dialog -> resultDialog = null)
            .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void play(int index) {
        if (computerDue != 0 || board[index] != 0 || outcome() != 0) return;
        board[index] = 1;
        if (outcome() == 0) {
            int delay = ThreadLocalRandom.current().nextInt(500, 1001);
            computerDue = SystemClock.elapsedRealtime() + delay;
            handler.postDelayed(computer, delay);
        }
        renderBoard();
        showResult();
    }

    private void computerMove() {
        computerDue = 0;
        if (outcome() == 0) {
            int bestMove = -1;
            int bestScore = Integer.MIN_VALUE;
            for (int i = 0; i < board.length; i++) {
                if (board[i] != 0) continue;
                board[i] = 2;
                int score = score(false, 0);
                board[i] = 0;
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = i;
                }
            }
            board[bestMove] = 2;
        }
        renderBoard();
        showResult();
    }

    private int score(boolean computerTurn, int depth) {
        int result = outcome();
        if (result == 2) return 10 - depth;
        if (result == 1) return depth - 10;
        if (result == 3) return 0;
        int best = computerTurn ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (int i = 0; i < board.length; i++) {
            if (board[i] != 0) continue;
            board[i] = computerTurn ? 2 : 1;
            int candidate = score(!computerTurn, depth + 1);
            board[i] = 0;
            best = computerTurn ? Math.max(best, candidate) : Math.min(best, candidate);
        }
        return best;
    }

    private int outcome() {
        for (int[] line : LINES) {
            int first = board[line[0]];
            if (first != 0 && first == board[line[1]] && first == board[line[2]]) return first;
        }
        for (int cell : board) {
            if (cell == 0) return 0;
        }
        return 3;
    }

    private void renderBoard() {
        int result = outcome();
        status.setText(result == 1 ? "你赢了" : result == 2 ? "电脑赢了" :
            result == 3 ? "平局" : computerDue != 0 ? "电脑思考中…" : "轮到你了 · 执 X");
        for (int i = 0; i < cells.length; i++) {
            String mark = board[i] == 1 ? "X" : board[i] == 2 ? "O" : "";
            cells[i].setText(mark);
            cells[i].setContentDescription("第" + (i / 3 + 1) + "行，第" + (i % 3 + 1) + "列，" +
                (mark.isEmpty() ? "空" : mark));
            cells[i].setEnabled(result == 0 && computerDue == 0 && board[i] == 0);
            cells[i].setTextColor(board[i] == 2 ? 0xff00665a : 0xff4738a1);
            GradientDrawable tile = new GradientDrawable();
            tile.setCornerRadius(dp(16));
            tile.setColor(board[i] == 1 ? 0xffe4ddff : board[i] == 2 ? 0xffcbf3e7 : 0xffeeebf8);
            cells[i].setBackground(tile);
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        updateRuntime();
        showResult();
        if (computerDue != 0) {
            handler.postDelayed(computer, Math.max(0, computerDue - SystemClock.elapsedRealtime()));
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(clock);
        handler.removeCallbacks(computer);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (resultDialog != null) resultDialog.dismiss();
        super.onDestroy();
    }

    private void updateRuntime() {
        long millis = SystemClock.elapsedRealtime() - STARTED_AT;
        long seconds = millis / 1000;
        StringBuilder text = new StringBuilder();
        appendUnit(text, seconds / 86400, "天");
        appendUnit(text, seconds / 3600 % 24, "小时");
        appendUnit(text, seconds / 60 % 60, "分钟");
        appendUnit(text, seconds % 60, "秒");
        runtime.setText(text.length() == 0 ? "0秒" : text.toString());
        handler.postDelayed(clock, 1000 - millis % 1000);
    }

    private void appendUnit(StringBuilder text, long amount, String unit) {
        if (amount != 0) text.append(amount).append(unit);
    }
}
