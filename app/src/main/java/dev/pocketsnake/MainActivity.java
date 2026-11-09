package dev.pocketsnake;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends Activity {
    private SnakeView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Match the page color and use dark icons so the clock stays readable.
        getWindow().setStatusBarColor(0xFFE6E9DE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        view = new SnakeView(this);
        setContentView(view);
    }

    @Override
    protected void onPause() {
        super.onPause();
        view.pauseGame();
    }
}
