package dev.pocketsnake;

import android.app.Activity;
import android.os.Bundle;

public class MainActivity extends Activity {
    private SnakeView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = new SnakeView(this);
        setContentView(view);
    }

    @Override
    protected void onPause() {
        super.onPause();
        view.pauseGame();
    }
}
