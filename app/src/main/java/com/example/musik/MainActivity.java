package com.example.musik;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private EditText searchInput;
    private ListView songList;

    // ===== МИНИ-ПЛЕЕР =====
    private TextView miniSongName, miniArtist, miniCurrentTime, miniDuration;
    private ImageButton miniPlayPause, miniNext, miniPrev;
    private SeekBar miniSeekBar;
    private View miniPlayer;
    private TextView miniPlaylistName;

    // ===== ПЛЕЙЛИСТЫ =====
    private Spinner playlistSpinner;
    private Button btnCreatePlaylist, btnDeletePlaylist, btnPlayPlaylist;
    private LinearLayout playlistControls;
    private ArrayAdapter<String> playlistAdapter;
    private List<String> playlistNames = new ArrayList<>();
    private int selectedPlaylistIndex = 0;

    private TextView genreAll, genrePop, genreRock, genreHiphop, genreElectronic, genreJazz, genreClassical;

    private MusicService musicService;
    private boolean isBound = false;
    private List<Song> allSongs = new ArrayList<>();
    private List<Song> displayedSongs = new ArrayList<>();
    private ArrayAdapter<Song> adapter;
    private String currentGenre = "Все";

    private final Handler handler = new Handler();
    private boolean isDragging = false;

    // ===== BROADCAST RECEIVER =====
    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (MusicService.ACTION_UPDATE.equals(action)) {
                if (musicService != null) {
                    allSongs = musicService.getSongList();
                    displayedSongs.clear();
                    displayedSongs.addAll(allSongs);
                    if (adapter != null) adapter.notifyDataSetChanged();

                    String songName = intent.getStringExtra("songName");
                    boolean isPlaying = intent.getBooleanExtra("isPlaying", false);
                    boolean isPlaylistMode = intent.getBooleanExtra("isPlaylistMode", false);
                    String playlistName = intent.getStringExtra("playlistName");

                    if (songName != null) {
                        miniSongName.setText(songName);
                        String artist = intent.getStringExtra("artist");
                        if (artist != null) miniArtist.setText(artist);
                        miniPlayer.setVisibility(View.VISIBLE);
                        if (isPlaying) {
                            miniPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                        } else {
                            miniPlayPause.setImageResource(android.R.drawable.ic_media_play);
                        }
                        if (isPlaylistMode && playlistName != null) {
                            miniPlaylistName.setText("📁 " + playlistName);
                            miniPlaylistName.setVisibility(View.VISIBLE);
                        } else {
                            miniPlaylistName.setVisibility(View.GONE);
                        }
                        updateMiniSeekBar();
                    }
                    updatePlaylistSpinner();
                }
            }
        }
    };

    // ===== ПОДКЛЮЧЕНИЕ К СЕРВИСУ =====
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            isBound = true;

            allSongs = musicService.getSongList();
            displayedSongs = new ArrayList<>(allSongs);

            adapter = new ArrayAdapter<Song>(MainActivity.this,
                    android.R.layout.simple_list_item_1, displayedSongs) {
                @Override
                public View getView(int position, View convertView, android.view.ViewGroup parent) {
                    if (convertView == null) {
                        convertView = getLayoutInflater().inflate(R.layout.song_item, parent, false);
                    }
                    Song song = getItem(position);
                    TextView name = convertView.findViewById(R.id.songName);
                    TextView artist = convertView.findViewById(R.id.songArtist);
                    TextView duration = convertView.findViewById(R.id.songDuration);
                    TextView index = convertView.findViewById(R.id.songIndex);
                    ImageButton playBtn = convertView.findViewById(R.id.songPlayBtn);
                    ImageButton addBtn = convertView.findViewById(R.id.songAddToPlaylist);

                    if (index != null) index.setText(String.valueOf(position + 1));
                    if (name != null) name.setText(song.name);
                    if (artist != null) artist.setText(song.artist);
                    if (duration != null) duration.setText("");

                    if (playBtn != null) {
                        playBtn.setOnClickListener(v -> {
                            if (isBound) {
                                int originalIndex = allSongs.indexOf(song);
                                musicService.playSong(originalIndex);
                                updateMiniPlayer();
                            }
                        });
                    }

                    // Кнопка "Добавить в плейлист"
                    if (addBtn != null) {
                        addBtn.setOnClickListener(v -> {
                            if (isBound) {
                                showAddToPlaylistDialog(song, position);
                            }
                        });
                        addBtn.setImageResource(android.R.drawable.ic_menu_add);
                        addBtn.setColorFilter(0xFF1DB954);
                    }

                    return convertView;
                }
            };
            songList.setAdapter(adapter);

            updatePlaylistSpinner();

            if (musicService.getCurrentIndex() >= 0) {
                updateMiniPlayer();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
        }
    };

    // ============================================================
    // ===== LIFECYCLE =====
    // ============================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        searchInput = findViewById(R.id.searchInput);
        songList = findViewById(R.id.songList);

        // ===== МИНИ-ПЛЕЕР =====
        miniSongName = findViewById(R.id.miniSongName);
        miniArtist = findViewById(R.id.miniArtist);
        miniCurrentTime = findViewById(R.id.miniCurrentTime);
        miniDuration = findViewById(R.id.miniDuration);
        miniSeekBar = findViewById(R.id.miniSeekBar);
        miniPlayPause = findViewById(R.id.miniPlayPause);
        miniNext = findViewById(R.id.miniNext);
        miniPrev = findViewById(R.id.miniPrev);
        miniPlayer = findViewById(R.id.miniPlayer);
        miniPlaylistName = findViewById(R.id.miniPlaylistName);

        // ===== ПЛЕЙЛИСТЫ =====
        playlistSpinner = findViewById(R.id.playlistSpinner);
        ImageButton btnCreatePlaylist = findViewById(R.id.btnCreatePlaylist);
        ImageButton btnDeletePlaylist = findViewById(R.id.btnDeletePlaylist);
        ImageButton btnPlayPlaylist = findViewById(R.id.btnPlayPlaylist);
        playlistControls = findViewById(R.id.playlistControls);

        genreAll = findViewById(R.id.genreAll);
        genrePop = findViewById(R.id.genrePop);
        genreRock = findViewById(R.id.genreRock);
        genreHiphop = findViewById(R.id.genreHiphop);
        genreElectronic = findViewById(R.id.genreElectronic);
        genreJazz = findViewById(R.id.genreJazz);
        genreClassical = findViewById(R.id.genreClassical);

        checkPermissions();

        Intent serviceIntent = new Intent(this, MusicService.class);
        startService(serviceIntent);
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).registerReceiver(updateReceiver,
                new IntentFilter(MusicService.ACTION_UPDATE));

        // ===== ПОИСК =====
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterSongs(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // ===== КЛИК ПО ТРЕКУ =====
        songList.setOnItemClickListener((parent, view, position, id) -> {
            if (position < displayedSongs.size() && isBound) {
                Song song = displayedSongs.get(position);
                int originalIndex = allSongs.indexOf(song);
                musicService.playSong(originalIndex);
                updateMiniPlayer();
            }
        });

        // ===== КНОПКИ МИНИ-ПЛЕЕРА =====
        miniPrev.setOnClickListener(v -> {
            if (isBound && musicService != null) {
                musicService.prev();
                updateMiniPlayer();
            }
        });

        miniPlayPause.setOnClickListener(v -> {
            if (isBound && musicService != null) {
                musicService.togglePlayPause();
                updateMiniPlayer();
            }
        });

        miniNext.setOnClickListener(v -> {
            if (isBound && musicService != null) {
                musicService.next();
                updateMiniPlayer();
            }
        });

        // ===== SEEK BAR =====
        miniSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && isBound && musicService != null) {
                    musicService.seekTo(progress);
                    miniCurrentTime.setText(formatTime(progress));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                isDragging = true;
            }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                isDragging = false;
            }
        });

        // ===== КЛИК ПО МИНИ-ПЛЕЕРУ =====
        miniPlayer.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, FullPlayerActivity.class);
            startActivity(intent);
        });

        // ===== ПЛЕЙЛИСТЫ: СПИННЕР =====
        playlistSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedPlaylistIndex = position;
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // ===== ПЛЕЙЛИСТЫ: СОЗДАТЬ =====
        btnCreatePlaylist.setOnClickListener(v -> showCreatePlaylistDialog());

        // ===== ПЛЕЙЛИСТЫ: УДАЛИТЬ =====
        btnDeletePlaylist.setOnClickListener(v -> {
            if (isBound && musicService != null) {
                musicService.deletePlaylist(selectedPlaylistIndex);
                updatePlaylistSpinner();
            }
        });

        // ===== ПЛЕЙЛИСТЫ: ВОСПРОИЗВЕСТИ =====
        btnPlayPlaylist.setOnClickListener(v -> {
            if (isBound && musicService != null) {
                musicService.playPlaylist(selectedPlaylistIndex);
                updateMiniPlayer();
            }
        });

        setupGenreListeners();
        miniPlayer.setVisibility(View.GONE);
        miniPlaylistName.setVisibility(View.GONE);
    }

    // ============================================================
    // ===== ПЛЕЙЛИСТЫ: UI =====
    // ============================================================

    private void updatePlaylistSpinner() {
        if (isBound && musicService != null) {
            List<Playlist> playlists = musicService.getPlaylists();
            playlistNames.clear();
            for (Playlist p : playlists) {
                playlistNames.add(p.name + " (" + p.songs.size() + ")");
            }
            if (playlistAdapter == null) {
                playlistAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, playlistNames);
                playlistAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                playlistSpinner.setAdapter(playlistAdapter);
            } else {
                playlistAdapter.notifyDataSetChanged();
            }
            if (selectedPlaylistIndex >= playlistNames.size()) {
                selectedPlaylistIndex = 0;
            }
            playlistSpinner.setSelection(selectedPlaylistIndex);
        }
    }

    private void showCreatePlaylistDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("📁 Создать плейлист");

        final EditText input = new EditText(this);
        input.setHint("Введите название");
        builder.setView(input);

        builder.setPositiveButton("Создать", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty() && isBound && musicService != null) {
                musicService.createPlaylist(name);
                updatePlaylistSpinner();
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void showAddToPlaylistDialog(Song song, int position) {
        if (!isBound || musicService == null) return;

        List<Playlist> playlists = musicService.getPlaylists();
        if (playlists.isEmpty()) {
            Toast.makeText(this, "❌ Нет плейлистов", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] items = new String[playlists.size()];
        for (int i = 0; i < playlists.size(); i++) {
            items[i] = playlists.get(i).name + " (" + playlists.get(i).songs.size() + ")";
        }

        new AlertDialog.Builder(this)
                .setTitle("Добавить в плейлист: " + song.name)
                .setItems(items, (dialog, which) -> {
                    if (isBound && musicService != null) {
                        musicService.addToPlaylist(which, position);
                        updatePlaylistSpinner();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // ============================================================
    // ===== ОБНОВЛЕНИЕ МИНИ-ПЛЕЕРА =====
    // ============================================================

    private void updateMiniPlayer() {
        if (musicService != null && musicService.getCurrentSong() != null) {
            Song song = musicService.getCurrentSong();
            miniSongName.setText(song.name);
            miniArtist.setText(song.artist);
            miniPlayer.setVisibility(View.VISIBLE);
            if (musicService.isPlaying()) {
                miniPlayPause.setImageResource(android.R.drawable.ic_media_pause);
            } else {
                miniPlayPause.setImageResource(android.R.drawable.ic_media_play);
            }
            if (musicService.isPlaylistMode()) {
                miniPlaylistName.setText("📁 " + musicService.getCurrentPlaylistName());
                miniPlaylistName.setVisibility(View.VISIBLE);
            } else {
                miniPlaylistName.setVisibility(View.GONE);
            }
            updateMiniSeekBar();
            updatePlaylistSpinner();
        }
    }

    private void updateMiniSeekBar() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isBound && musicService != null && !isDragging) {
                    int current = musicService.getCurrentPosition();
                    int duration = musicService.getDuration();

                    if (duration > 0) {
                        miniSeekBar.setMax(duration);
                        miniSeekBar.setProgress(current);
                    }

                    miniCurrentTime.setText(formatTime(current));
                    miniDuration.setText(formatTime(duration));

                    if (musicService.isPlaying()) {
                        updateMiniSeekBar();
                    }
                }
            }
        }, 500);
    }

    // ============================================================
    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====
    // ============================================================

    private String formatTime(int ms) {
        if (ms <= 0) return "0:00";
        int totalSec = ms / 1000;
        int m = totalSec / 60;
        int s = totalSec % 60;
        return String.format("%d:%02d", m, s);
    }

    public void forceRefresh(View v) {
        if (isBound && musicService != null) {
            Toast.makeText(this, "🔄 Обновление...", Toast.LENGTH_SHORT).show();
            musicService.loadFromNavidrome();
        } else {
            Toast.makeText(this, "❌ Сервис не подключен", Toast.LENGTH_SHORT).show();
        }
    }

    public void searchMusic(View view) {
        String query = searchInput.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, "Введите запрос", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isBound && musicService != null) {
            Toast.makeText(this, "🔍 Ищем: " + query, Toast.LENGTH_SHORT).show();
        }
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
            }
        }
    }

    private void setupGenreListeners() {
        View.OnClickListener genreListener = v -> {
            resetGenreStyles();
            TextView tv = (TextView) v;
            tv.setBackgroundResource(R.drawable.genre_selected);
            currentGenre = tv.getText().toString();
            filterSongs(searchInput.getText().toString());
        };

        genreAll.setOnClickListener(genreListener);
        genrePop.setOnClickListener(genreListener);
        genreRock.setOnClickListener(genreListener);
        genreHiphop.setOnClickListener(genreListener);
        genreElectronic.setOnClickListener(genreListener);
        genreJazz.setOnClickListener(genreListener);
        genreClassical.setOnClickListener(genreListener);
    }

    private void resetGenreStyles() {
        genreAll.setBackgroundResource(R.drawable.genre_unselected);
        genrePop.setBackgroundResource(R.drawable.genre_unselected);
        genreRock.setBackgroundResource(R.drawable.genre_unselected);
        genreHiphop.setBackgroundResource(R.drawable.genre_unselected);
        genreElectronic.setBackgroundResource(R.drawable.genre_unselected);
        genreJazz.setBackgroundResource(R.drawable.genre_unselected);
        genreClassical.setBackgroundResource(R.drawable.genre_unselected);
    }

    private void filterSongs(String query) {
        displayedSongs.clear();
        for (Song song : allSongs) {
            boolean matchesGenre = currentGenre.equals("Все") ||
                    (song.genre != null && song.genre.equals(currentGenre));
            boolean matchesQuery = song.name.toLowerCase().contains(query.toLowerCase()) ||
                    song.artist.toLowerCase().contains(query.toLowerCase());
            if (matchesGenre && matchesQuery) {
                displayedSongs.add(song);
            }
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isBound && musicService != null && musicService.getCurrentSong() != null) {
            updateMiniPlayer();
        }
        updatePlaylistSpinner();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(updateReceiver);
        handler.removeCallbacksAndMessages(null);
    }
}