package dev.pocketsnake;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** The rules of Snake with no Android classes, so they can be tested anywhere. */
public class SnakeGame {
    public static final int UP = 0;
    public static final int RIGHT = 1;
    public static final int DOWN = 2;
    public static final int LEFT = 3;

    public enum StepResult { MOVED, ATE, DIED, WON }

    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DY = {-1, 0, 1, 0};
    private static final int START_MS = 150;
    private static final int MIN_MS = 75;

    private final int size;
    private final Random rng;
    // Index 0 is the head.
    private final List<int[]> snake = new ArrayList<>();
    private final ArrayDeque<Integer> turns = new ArrayDeque<>();
    private int dir;
    private int foodX = -1;
    private int foodY = -1;
    private int score;

    public SnakeGame(int size, Random rng) {
        this.size = size;
        this.rng = rng;
        reset();
    }

    public void reset() {
        int m = size / 2;
        snake.clear();
        snake.add(new int[]{m, m});
        snake.add(new int[]{m - 1, m});
        snake.add(new int[]{m - 2, m});
        turns.clear();
        dir = RIGHT;
        score = 0;
        placeFood();
    }

    /** Queues a turn. Up to two turns are buffered; reversing or repeating is ignored. */
    public void turn(int d) {
        int ref = turns.isEmpty() ? dir : turns.peekLast();
        if (d == ref || d == (ref + 2) % 4) return;
        if (turns.size() < 2) turns.add(d);
    }

    public StepResult step() {
        if (!turns.isEmpty()) dir = turns.poll();
        int nx = snake.get(0)[0] + DX[dir];
        int ny = snake.get(0)[1] + DY[dir];
        if (nx < 0 || ny < 0 || nx >= size || ny >= size) return StepResult.DIED;

        boolean eating = nx == foodX && ny == foodY;
        // The tail cell frees up this step unless the snake is growing.
        int limit = eating ? snake.size() : snake.size() - 1;
        for (int i = 0; i < limit; i++) {
            int[] s = snake.get(i);
            if (s[0] == nx && s[1] == ny) return StepResult.DIED;
        }

        snake.add(0, new int[]{nx, ny});
        if (!eating) {
            snake.remove(snake.size() - 1);
            return StepResult.MOVED;
        }
        score++;
        return placeFood() ? StepResult.ATE : StepResult.WON;
    }

    /** Milliseconds between steps; the snake speeds up as the score grows. */
    public int stepDelayMs() {
        return Math.max(MIN_MS, START_MS - score * 3);
    }

    private boolean placeFood() {
        boolean[][] taken = new boolean[size][size];
        for (int[] s : snake) taken[s[0]][s[1]] = true;
        List<int[]> free = new ArrayList<>();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (!taken[x][y]) free.add(new int[]{x, y});
            }
        }
        if (free.isEmpty()) {
            foodX = -1;
            foodY = -1;
            return false;
        }
        int[] f = free.get(rng.nextInt(free.size()));
        foodX = f[0];
        foodY = f[1];
        return true;
    }

    /** Test hook: put the food on a chosen cell. */
    void setFood(int x, int y) {
        foodX = x;
        foodY = y;
    }

    public int getScore() { return score; }
    public int snakeLength() { return snake.size(); }
    public int snakeX(int i) { return snake.get(i)[0]; }
    public int snakeY(int i) { return snake.get(i)[1]; }
    public boolean hasFood() { return foodX >= 0; }
    public int foodX() { return foodX; }
    public int foodY() { return foodY; }
}
