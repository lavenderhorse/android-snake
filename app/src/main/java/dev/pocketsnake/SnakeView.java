package dev.pocketsnake;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

/** Draws the board like a pocket LCD and turns swipes into turns. */
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

    private static final float[][] FOOD_OFFSETS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    private final SnakeGame game = new SnakeGame(N, new Random());
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SharedPreferences prefs;
    private final float density;

    private int state = READY;
    private int best;
    private boolean newBest;
    private boolean won;
    private float lastX;
    private float lastY;
    private boolean swiped;

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

    // ----- input -----

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = e.getX();
                lastY = e.getY();
                swiped = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - lastX;
                float dy = e.getY() - lastY;
                if (Math.max(Math.abs(dx), Math.abs(dy)) >= 24 * density) {
                    int d;
                    if (Math.abs(dx) > Math.abs(dy)) {
                        d = dx > 0 ? SnakeGame.RIGHT : SnakeGame.LEFT;
                    } else {
                        d = dy > 0 ? SnakeGame.DOWN : SnakeGame.UP;
                    }
                    handleDirection(d);
                    lastX = e.getX();
                    lastY = e.getY();
                    swiped = true;
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (!swiped) handleTap();
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
        float w = getWidth();
        float h = getHeight();
        c.drawColor(PAGE);

        float pad = 16 * density;
        float boardTop = pad + 52 * density;
        float footer = 44 * density;
        float size = Math.min(w - 2 * pad, h - boardTop - footer - pad);
        if (size < 50 * density) size = 50 * density;
        float left = (w - size) / 2f;
        float right = left + size;
        float bottom = boardTop + size;
        float cs = size / N;
        float corner = 6 * density;

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
        c.drawRoundRect(left, boardTop, right, bottom, corner, corner, paint);
        paint.setColor(PX_OFF);
        float p = cs * 0.1f;
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                c.drawRect(left + x * cs + p, boardTop + y * cs + p,
                        left + (x + 1) * cs - p, boardTop + (y + 1) * cs - p, paint);
            }
        }

        // Snake: the head is slightly larger than the body.
        paint.setColor(INK);
        for (int i = game.snakeLength() - 1; i >= 0; i--) {
            float q = cs * (i == 0 ? 0.03f : 0.1f);
            float x = left + game.snakeX(i) * cs;
            float y = boardTop + game.snakeY(i) * cs;
            c.drawRect(x + q, y + q, x + cs - q, y + cs - q, paint);
        }

        // Food: four small pixels in a diamond.
        if (game.hasFood()) {
            float u = cs * 0.3f;
            float cx = left + (game.foodX() + 0.5f) * cs;
            float cy = boardTop + (game.foodY() + 0.5f) * cs;
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
        c.drawRoundRect(left, boardTop, right, bottom, corner, corner, paint);
        paint.setStyle(Paint.Style.FILL);

        if (state != PLAYING) {
            paint.setColor(VEIL);
            c.drawRoundRect(left, boardTop, right, bottom, corner, corner, paint);

            String title;
            String line1;
            String line2;
            if (state == READY) {
                title = "READY?";
                line1 = "Swipe to start and steer.";
                line2 = "Tap to pause.";
            } else if (state == PAUSED) {
                title = "PAUSED";
                line1 = "Tap to resume.";
                line2 = "";
            } else {
                title = won ? "YOU WIN" : "GAME OVER";
                line1 = "Score " + game.getScore() + (newBest ? " - new best" : "");
                line2 = "Tap to play again.";
            }

            float midX = left + size / 2f;
            float midY = boardTop + size / 2f;
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(INK);
            paint.setTextSize(26 * density);
            c.drawText(title, midX, midY - 8 * density, paint);
            paint.setColor(INK_SOFT);
            paint.setTextSize(13 * density);
            c.drawText(line1, midX, midY + 16 * density, paint);
            c.drawText(line2, midX, midY + 36 * density, paint);
        }

        // Footer hint.
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(INK_SOFT);
        paint.setTextSize(12 * density);
        c.drawText("Swipe anywhere to steer", w / 2f, bottom + 30 * density, paint);
    }
}
