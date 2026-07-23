package com.example.musik;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class FullPlayerActivity extends AppCompatActivity {

    private TextView fullSongName, fullArtist, fullCurrentTime, fullDuration;
    private ImageButton fullPlayPause, fullPrev, fullNext, fullClose;
    private ImageButton fullShuffle, fullRepeat;
    private SeekBar fullSeekBar;
    private View fullPlayerRoot;

    private MusicService musicService;
    private boolean isBound = false;
    private Handler handler = new Handler();
    private boolean isDragging = false;
    private float startY;

    private final int COLOR_GREEN = 0xFF1DB954;
    private final int COLOR_GREY = 0xFF888888;

    // ===== ТАЙМЕР ДЛЯ ОБНОВЛЕНИЯ =====
    private boolean isUpdating = false;

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isUpdating || !isBound || musicService == null) return;
            updateUI();
            handler.postDelayed(this, 500);
        }
    };

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            isBound = true;
            updateUI();
            startUpdating();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
            stopUpdating();
        }
    };

    private void startUpdating() {
        if (isUpdating) return;
        isUpdating = true;
        handler.post(updateRunnable);
    }

    private void stopUpdating() {
        isUpdating = false;
        handler.removeCallbacks(updateRunnable);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_player);

        fullSongName = findViewById(R.id.fullSongName);
        fullArtist = findViewById(R.id.fullArtist);
        fullCurrentTime = findViewById(R.id.fullCurrentTime);
        fullDuration = findViewById(R.id.fullDuration);
        fullSeekBar = findViewById(R.id.fullSeekBar);
        fullPlayPause = findViewById(R.id.fullPlayPause);
        fullPrev = findViewById(R.id.fullPrev);
        fullNext = findViewById(R.id.fullNext);
        fullClose = findViewById(R.id.fullClose);
        fullShuffle = findViewById(R.id.fullShuffle);
        fullRepeat = findViewById(R.id.fullRepeat);
        fullPlayerRoot = findViewById(R.id.fullPlayerRoot);

        Intent serviceIntent = new Intent(this, MusicService.class);
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);

        // ===== КНОПКИ =====
        fullPlayPause.setOnClickListener(v -> {
            if (isBound) {
                musicService.togglePlayPause();
                updateUI();
            }
        });

        fullPrev.setOnClickListener(v -> {
            if (isBound) {
                musicService.prev();
                updateUI();
            }
        });

        fullNext.setOnClickListener(v -> {
            if (isBound) {
                musicService.next();
                updateUI();
            }
        });

        fullClose.setOnClickListener(v -> finish());

        fullShuffle.setOnClickListener(v -> {
            if (isBound) {
                musicService.toggleShuffle();
                updateUI();
                updateShuffleIcon();
            }
        });

        fullRepeat.setOnClickListener(v -> {
            if (isBound) {
                musicService.toggleRepeat();
                updateUI();
                updateRepeatIcon();
            }
        });

        fullSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && isBound) {
                    musicService.seekTo(progress);
                    fullCurrentTime.setText(formatTime(progress));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                isDragging = true;
            }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                isDragging = false;
            }
        });

        fullPlayerRoot.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startY = event.getY();
                    isDragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float deltaY = event.getY() - startY;
                    if (deltaY > 50) isDragging = true;
                    if (isDragging) {
                        v.setAlpha(1 - Math.min(deltaY / 500, 1));
                        v.setTranslationY(deltaY / 2);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (isDragging && event.getY() - startY > 200) {
                        finish();
                    } else {
                        v.setAlpha(1);
                        v.setTranslationY(0);
                    }
                    v.performClick();
                    return true;
            }
            return false;
        });
    }

    // ===== ОБНОВЛЕНИЕ ICON =====
    private void updateShuffleIcon() {
        if (isBound && musicService != null) {
            if (musicService.isShuffleEnabled()) {
                fullShuffle.setColorFilter(COLOR_GREEN, PorterDuff.Mode.SRC_IN);
            } else {
                fullShuffle.setColorFilter(COLOR_GREY, PorterDuff.Mode.SRC_IN);
            }
        }
    }

    private void updateRepeatIcon() {
        if (isBound && musicService != null) {
            int mode = musicService.getRepeatMode();
            switch (mode) {
                case MusicService.REPEAT_NONE:
                    fullRepeat.setColorFilter(COLOR_GREY, PorterDuff.Mode.SRC_IN);
                    fullRepeat.setImageResource(R.drawable.ic_repeat_none);
                    break;
                case MusicService.REPEAT_ONE:
                    fullRepeat.setColorFilter(COLOR_GREEN, PorterDuff.Mode.SRC_IN);
                    fullRepeat.setImageResource(R.drawable.ic_repeat_one);
                    break;
                case MusicService.REPEAT_ALL:
                    fullRepeat.setColorFilter(COLOR_GREEN, PorterDuff.Mode.SRC_IN);
                    fullRepeat.setImageResource(R.drawable.ic_repeat_all);
                    break;
            }
        }
    }

    // ===== ОБНОВЛЕНИЕ ВСЕГО UI =====
    private void updateUI() {
        if (isBound && musicService != null) {
            Song song = musicService.getCurrentSong();

            if (song != null) {
                fullSongName.setText(song.name);
                fullArtist.setText(song.artist);
            } else {
                fullSongName.setText("⏳ Загрузка...");
                fullArtist.setText("---");
            }

            int current = musicService.getCurrentPosition();
            int duration = musicService.getDuration();

            fullSeekBar.setMax(duration);
            fullSeekBar.setProgress(current);
            fullCurrentTime.setText(formatTime(current));
            fullDuration.setText(formatTime(duration));

            if (musicService.isPlaying()) {
                fullPlayPause.setImageResource(android.R.drawable.ic_media_pause);
            } else {
                fullPlayPause.setImageResource(android.R.drawable.ic_media_play);
            }

            updateRepeatIcon();
            updateShuffleIcon();
        }
    }

    private String formatTime(int ms) {
        if (ms <= 0) return "0:00";
        int totalSec = ms / 1000;
        int m = totalSec / 60;
        int s = totalSec % 60;
        return String.format("%d:%02d", m, s);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isBound) {
            updateUI();
            startUpdating();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopUpdating();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopUpdating();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
        handler.removeCallbacksAndMessages(null);
    }
}