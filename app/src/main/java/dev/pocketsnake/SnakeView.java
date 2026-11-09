package dev.pocketsnake;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

/** Draws the board like a pocket LCD, with a d-pad below it. Swipes steer too. */
public class SnakeView extends View {
    private static final int N = 16;

    private static final int READY = 0;
    private static final int PLAYING = 1;
    private static final int PAUSED = 2;
    private static final int OVER = 3;

    private static final int PAGE = 0xFFE6E9DE;
    private static final int INK = 0xFF25331F;
    private static final int INK_SOFT = 0xFF566550;
    private static final int SCREEN = 0xFFB9C9A0;
    private static final int PX_OFF = 0x1725331F;
    private static final int VEIL = 0xE6B9C9A0;
    private static final int KEY = 0xFFD6DCCB;
    private static final int KEY_EDGE = 0xFFA9B49B;
    private static final int KEY_DOWN = 0xFFC4CCB5;

    private static final float[][] FOOD_OFFSETS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    private final SnakeGame game = new SnakeGame(N, new Random());
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private final SharedPreferences prefs;
    private final float density;

    // Layout, recomputed from the view size. keys[d] = left, top, right, bottom,
    // indexed by direction (UP, RIGHT, DOWN, LEFT).
    private float boardLeft;
    private float boardTop;
    private float boardSize;
    private final float[][] keys = new float[4][4];

    private int state = READY;
    private int best;
    private boolean newBest;
    private boolean won;
    private float lastX;
    private float lastY;
    private boolean swiped;
    private boolean onPad;
    private int pressed = -1;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (state != PLAYING) return;
            SnakeGame.StepResult r = game.step();
            if (r == SnakeGame.StepResult.DIED || r == SnakeGame.StepResult.WON) {
                finish(r == SnakeGame.StepResult.WON);
            } else {
                handler.postDelayed(this, game.stepDelayMs());
            }
            invalidate();
        }
    };

    public SnakeView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        prefs = context.getSharedPreferences("pocket-snake", Context.MODE_PRIVATE);
        best = prefs.getInt("best", 0);
    }

    // ----- game flow -----

    private void startGame() {
        game.reset();
        newBest = false;
        won = false;
        state = PLAYING;
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, game.stepDelayMs());
        invalidate();
    }

    public void pauseGame() {
        if (state != PLAYING) return;
        state = PAUSED;
        handler.removeCallbacks(tick);
        invalidate();
    }

    private void resumeGame() {
        state = PLAYING;
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, game.stepDelayMs());
        invalidate();
    }

    private void finish(boolean wonGame) {
        state = OVER;
        won = wonGame;
        newBest = game.getScore() > best;
        if (newBest) {
            best = game.getScore();
            prefs.edit().putInt("best", best).apply();
        }
    }

    private void handleDirection(int d) {
        if (state == READY) startGame();
        if (state == PLAYING) game.turn(d);
    }

    private void handleTap() {
        if (state == PLAYING) pauseGame();
        else if (state == PAUSED) resumeGame();
        else startGame();
    }

    // ----- layout -----

    private void setKey(int d, float x, float y, float size) {
        keys[d][0] = x;
        keys[d][1] = y;
        keys[d][2] = x + size;
        keys[d][3] = y + size;
    }

    private void layout() {
        float w = getWidth();
        float h = getHeight();
        float pad = 16 * density;
        float gap = 8 * density;
        float footer = 44 * density;
        // Space kept free under the board for the d-pad at its smallest size.
        float padMin = 3 * 56 * density + 2 * gap + 12 * density;

        boardTop = pad + 52 * density;
        boardSize = Math.min(w - 2 * pad, h - boardTop - footer - padMin);
        if (boardSize < 50 * density) boardSize = 50 * density;
        boardLeft = (w - boardSize) / 2f;

        float areaTop = boardTop + boardSize + footer;
        float areaH = h - areaTop - pad;
        float k = Math.min(72 * density, (areaH - 2 * gap) / 3f);
        if (k < 40 * density) k = 40 * density;
        float total = 3 * k + 2 * gap;
        float px = (w - total) / 2f;
        float py = areaTop + Math.max(0f, (areaH - total) / 2f);
        setKey(SnakeGame.UP, px + k + gap, py, k);
        setKey(SnakeGame.LEFT, px, py + k + gap, k);
        setKey(SnakeGame.RIGHT, px + 2 * (k + gap), py + k + gap, k);
        setKey(SnakeGame.DOWN, px + k + gap, py + 2 * (k + gap), k);
    }

    private int keyAt(float x, float y) {
        float slop = 4 * density;
        for (int d = 0; d < 4; d++) {
            float[] r = keys[d];
            if (x >= r[0] - slop && x <= r[2] + slop && y >= r[1] - slop && y <= r[3] + slop) {
                return d;
            }
        }
        return -1;
    }

    // ----- input -----

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        layout();
        float x = e.getX();
        float y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                int k = keyAt(x, y);
                if (k >= 0) {
                    onPad = true;
                    pressed = k;
                    handleDirection(k);
                    invalidate();
                    return true;
                }
                onPad = false;
                lastX = x;
                lastY = y;
                swiped = false;
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (onPad) {
                    // Sliding onto another key presses it.
                    int k = keyAt(x, y);
                    if (k >= 0 && k != pressed) {
                        pressed = k;
                        handleDirection(k);
                        invalidate();
                    }
                    return true;
                }
                float dx = x - lastX;
                float dy = y - lastY;
                if (Math.max(Math.abs(dx), Math.abs(dy)) >= 24 * density) {
                    int d;
                    if (Math.abs(dx) > Math.abs(dy)) {
                        d = dx > 0 ? SnakeGame.RIGHT : SnakeGame.LEFT;
                    } else {
                        d = dy > 0 ? SnakeGame.DOWN : SnakeGame.UP;
                    }
                    handleDirection(d);
                    lastX = x;
                    lastY = y;
                    swiped = true;
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (onPad) {
                    onPad = false;
                    pressed = -1;
                    invalidate();
                } else if (!swiped) {
                    handleTap();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                onPad = false;
                pressed = -1;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    // ----- drawing -----

    private static String pad3(int n) {
        String s = String.valueOf(n);
        while (s.length() < 3) s = "0" + s;
        return s;
    }

    @Override
    protected void onDraw(Canvas c) {
        layout();
        float w = getWidth();
        float pad = 16 * density;
        float left = boardLeft;
        float right = left + boardSize;
        float top = boardTop;
        float bottom = top + boardSize;
        float cs = boardSize / N;
        float corner = 6 * density;

        c.drawColor(PAGE);

        // Header: title on the left, scores on the right.
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        paint.setColor(INK);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(20 * density);
        c.drawText("POCKET SNAKE", left, pad + 22 * density, paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTextSize(14 * density);
        c.drawText("SCORE " + pad3(game.getScore()), right, pad + 14 * density, paint);
        paint.setColor(INK_SOFT);
        c.drawText("BEST  " + pad3(best), right, pad + 32 * density, paint);

        // Screen and the faint grid of unlit pixels.
        paint.setColor(SCREEN);
        c.drawRoundRect(left, top, right, bottom, corner, corner, paint);
        paint.setColor(PX_OFF);
        float p = cs * 0.1f;
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                c.drawRect(left + x * cs + p, top + y * cs + p,
                        left + (x + 1) * cs - p, top + (y + 1) * cs - p, paint);
            }
        }

        // Snake: the head is slightly larger than the body.
        paint.setColor(INK);
        for (int i = game.snakeLength() - 1; i >= 0; i--) {
            float q = cs * (i == 0 ? 0.03f : 0.1f);
            float x = left + game.snakeX(i) * cs;
            float y = top + game.snakeY(i) * cs;
            c.drawRect(x + q, y + q, x + cs - q, y + cs - q, paint);
        }

        // Food: four small pixels in a diamond.
        if (game.hasFood()) {
            float u = cs * 0.3f;
            float cx = left + (game.foodX() + 0.5f) * cs;
            float cy = top + (game.foodY() + 0.5f) * cs;
            for (float[] o : FOOD_OFFSETS) {
                float px = cx + o[0] * u;
                float py = cy + o[1] * u;
                c.drawRect(px - u / 2, py - u / 2, px + u / 2, py + u / 2, paint);
            }
        }

        // Screen border.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3 * density);
        paint.setColor(INK);
        c.drawRoundRect(left, top, right, bottom, corner, corner, paint);
        paint.setStyle(Paint.Style.FILL);

        if (state != PLAYING) {
            paint.setColor(VEIL);
            c.drawRoundRect(left, top, right, bottom, corner, corner, paint);

            String title;
            String line1;
            String line2;
            if (state == READY) {
                title = "READY?";
                line1 = "Swipe or use the pad to start.";
                line2 = "Tap the board to pause.";
            } else if (state == PAUSED) {
                title = "PAUSED";
                line1 = "Tap the board to resume.";
                line2 = "";
            } else {
                title = won ? "YOU WIN" : "GAME OVER";
                line1 = "Score " + game.getScore() + (newBest ? " - new best" : "");
                line2 = "Tap the board to play again.";
            }

            float midX = left + boardSize / 2f;
            float midY = top + boardSize / 2f;
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(INK);
            paint.setTextSize(26 * density);
            c.drawText(title, midX, midY - 8 * density, paint);
            paint.setColor(INK_SOFT);
            paint.setTextSize(13 * density);
            c.drawText(line1, midX, midY + 16 * density, paint);
            c.drawText(line2, midX, midY + 36 * density, paint);
        }

        // Hint under the board.
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(INK_SOFT);
        paint.setTextSize(12 * density);
        c.drawText("Swipe or use the pad to steer", w / 2f, bottom + 30 * density, paint);

        // D-pad: four keys, each with an arrow that points the way it steers.
        for (int d = 0; d < 4; d++) {
            float[] r = keys[d];
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(pressed == d ? KEY_DOWN : KEY);
            c.drawRoundRect(r[0], r[1], r[2], r[3], 10 * density, 10 * density, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f * density);
            paint.setColor(KEY_EDGE);
            c.drawRoundRect(r[0], r[1], r[2], r[3], 10 * density, 10 * density, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(INK);
            float cx = (r[0] + r[2]) / 2f;
            float cy = (r[1] + r[3]) / 2f;
            float a = (r[2] - r[0]) * 0.22f;
            arrow.reset();
            arrow.moveTo(cx, cy - a);
            arrow.lineTo(cx + a * 1.2f, cy + a * 0.9f);
            arrow.lineTo(cx - a * 1.2f, cy + a * 0.9f);
            arrow.close();
            c.save();
            c.rotate(d * 90f, cx, cy);
            c.drawPath(arrow, paint);
            c.restore();
        }
        paint.setStyle(Paint.Style.FILL);
    }
}
