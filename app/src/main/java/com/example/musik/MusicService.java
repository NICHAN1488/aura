package com.example.musik;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MusicService extends Service implements Player.Listener {

    private static final String TAG = "MusicService";
    public static final String ACTION_PLAY = "ACTION_PLAY";
    public static final String ACTION_PAUSE = "ACTION_PAUSE";
    public static final String ACTION_NEXT = "ACTION_NEXT";
    public static final String ACTION_PREV = "ACTION_PREV";
    public static final String ACTION_STOP = "ACTION_STOP";
    public static final String ACTION_UPDATE = "ACTION_UPDATE";
    public static final String ACTION_REPEAT = "ACTION_REPEAT";
    public static final String ACTION_SHUFFLE = "ACTION_SHUFFLE";

    public static final int REPEAT_NONE = 0;
    public static final int REPEAT_ONE = 1;
    public static final int REPEAT_ALL = 2;

    private ExoPlayer exoPlayer;
    private List<Song> songList = new ArrayList<>();
    private List<Song> originalSongList = new ArrayList<>();
    private int currentIndex = -1;
    private boolean isPlaying = false;
    private Handler handler = new Handler();
    private NotificationManagerCompat notificationManager;
    private boolean isPreparing = false;

    private int repeatMode = REPEAT_NONE;
    private boolean shuffleEnabled = false;

    private List<Playlist> playlists = new ArrayList<>();
    private boolean isPlaylistMode = false;

    private final IBinder binder = new MusicBinder();

    private static final String SERVER_URL = "custom";
    private static final String USERNAME = "custom";
    private static final String PASSWORD = "custom";

    public class MusicBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "🔥 MUSIC SERVICE СОЗДАН!");
        initExoPlayer();
        notificationManager = NotificationManagerCompat.from(this);
        createNotificationChannel();
        loadFromNavidrome();
        loadPlaylists();
    }

    private void initExoPlayer() {
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(this);
        trackSelector.setParameters(
                new DefaultTrackSelector.Parameters.Builder()
                        .setMaxVideoSize(0, 0)
                        .setMaxVideoBitrate(0)
                        .build()
        );

        exoPlayer = new ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .build();

        exoPlayer.addListener(this);
    }

    private void savePlaylists() {
        SharedPreferences prefs = getSharedPreferences("playlists", MODE_PRIVATE);
    }

    private void loadPlaylists() {
        playlists = new ArrayList<>();
    }

    private String detectGenre(String songName, String artistName) {
        String lowerName = songName.toLowerCase();
        String lowerArtist = artistName.toLowerCase();

        if (lowerName.contains("pop") || lowerArtist.contains("pop") || lowerName.contains("поп")) return "Поп";
        if (lowerName.contains("rock") || lowerArtist.contains("rock") || lowerName.contains("рок")) return "Рок";
        if (lowerName.contains("rap") || lowerArtist.contains("rap") || lowerName.contains("рэп")) return "Рэп";
        if (lowerName.contains("hip") || lowerArtist.contains("hip") || lowerName.contains("хип")) return "Хип-хоп";
        if (lowerName.contains("jazz") || lowerArtist.contains("jazz") || lowerName.contains("джаз")) return "Джаз";
        if (lowerName.contains("classic") || lowerArtist.contains("classic") || lowerName.contains("классик")) return "Классика";
        if (lowerName.contains("electronic") || lowerArtist.contains("electronic") || lowerName.contains("электро")) return "Электроника";

        return "Поп";
    }

    public void loadFromNavidrome() {
        new Thread(() -> {
            try {
                String server = SERVER_URL;
                String user = USERNAME;
                String pass = PASSWORD;

                String urlString = server + "/rest/getAlbumList2?u=" + user + "&p=" + pass +
                        "&v=1.16.1&c=myplayer&f=json&type=random&size=50";

                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                int responseCode = connection.getResponseCode();

                if (responseCode != 200) {
                    connection.disconnect();
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    json.append(line);
                }
                reader.close();
                connection.disconnect();

                JSONObject obj = new JSONObject(json.toString());
                JSONObject subsonic = obj.getJSONObject("subsonic-response");
                JSONObject albumList = subsonic.getJSONObject("albumList2");
                JSONArray albums = albumList.getJSONArray("album");

                songList.clear();

                for (int i = 0; i < albums.length(); i++) {
                    JSONObject album = albums.getJSONObject(i);
                    String albumId = album.getString("id");

                    String songsUrl = server + "/rest/getAlbum?u=" + user + "&p=" + pass +
                            "&v=1.16.1&c=myplayer&f=json&id=" + albumId;

                    URL songsUrlObj = new URL(songsUrl);
                    HttpURLConnection songsConn = (HttpURLConnection) songsUrlObj.openConnection();
                    songsConn.setRequestMethod("GET");
                    songsConn.setConnectTimeout(15000);
                    songsConn.setReadTimeout(15000);

                    BufferedReader songsReader = new BufferedReader(new InputStreamReader(songsConn.getInputStream()));
                    StringBuilder songsJson = new StringBuilder();
                    String songsLine;
                    while ((songsLine = songsReader.readLine()) != null) {
                        songsJson.append(songsLine);
                    }
                    songsReader.close();
                    songsConn.disconnect();

                    JSONObject songsObj = new JSONObject(songsJson.toString());
                    JSONObject songsSubsonic = songsObj.getJSONObject("subsonic-response");
                    JSONObject albumDetail = songsSubsonic.getJSONObject("album");
                    JSONArray songs = albumDetail.getJSONArray("song");

                    for (int j = 0; j < songs.length(); j++) {
                        JSONObject song = songs.getJSONObject(j);
                        String songName = song.getString("title");
                        String artistName = song.getString("artist");
                        String songId = song.getString("id");

                        String streamUrl = server + "/rest/stream?id=" + songId +
                                "&u=" + user + "&p=" + pass + "&v=1.16.1&c=myplayer";

                        String genre = "Поп";
                        if (song.has("genre") && !song.isNull("genre")) {
                            genre = song.getString("genre");
                        }

                        songList.add(new Song(songName, artistName, streamUrl, 0, genre, songId));
                    }
                }

                originalSongList = new ArrayList<>(songList);

                final int songCount = songList.size();
                handler.post(() -> {
                    Toast.makeText(MusicService.this,
                            "✅ Загружено: " + songCount + " треков", Toast.LENGTH_SHORT).show();
                    sendUpdateBroadcast();
                });

            } catch (Exception e) {
                Log.e(TAG, "Ошибка загрузки", e);
            }
        }).start();
    }

    public void playSong(int index) {
        if (index < 0 || index >= songList.size()) return;
        if (isPreparing) return;

        Song song = songList.get(index);
        if (song == null || song.path == null || song.path.isEmpty()) {
            handler.post(() -> Toast.makeText(MusicService.this, "Нет ссылки на трек", Toast.LENGTH_SHORT).show());
            return;
        }

        currentIndex = index;
        isPreparing = true;

        try {
            MediaItem mediaItem = MediaItem.fromUri(song.path);
            exoPlayer.clearMediaItems();
            exoPlayer.setMediaItem(mediaItem);
            exoPlayer.prepare();
            exoPlayer.setPlayWhenReady(true);

            isPlaying = true;
            isPreparing = false;
            updateNotification();

        } catch (Exception e) {
            e.printStackTrace();
            isPreparing = false;
            handler.post(() -> Toast.makeText(MusicService.this, "❌ Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    public void play() {
        if (exoPlayer != null && !isPlaying && !isPreparing) {
            exoPlayer.setPlayWhenReady(true);
            isPlaying = true;
            updateNotification();
        }
    }

    public void pause() {
        if (exoPlayer != null && isPlaying) {
            exoPlayer.setPlayWhenReady(false);
            isPlaying = false;
            updateNotification();
        }
    }

    public void togglePlayPause() {
        if (isPlaying) {
            pause();
        } else {
            play();
        }
    }

    public void next() {
        if (songList.isEmpty()) return;

        if (repeatMode == REPEAT_ONE) {
            playSong(currentIndex);
            return;
        }

        if (currentIndex >= songList.size() - 1) {
            if (repeatMode == REPEAT_ALL) {
                playSong(0);
            } else {
                pause();
            }
        } else {
            playSong(currentIndex + 1);
        }
    }

    public void prev() {
        if (songList.isEmpty()) return;

        if (currentIndex <= 0) {
            playSong(songList.size() - 1);
        } else {
            playSong(currentIndex - 1);
        }
    }

    public void toggleShuffle() {
        shuffleEnabled = !shuffleEnabled;
        if (shuffleEnabled) {
            Song current = null;
            if (currentIndex >= 0 && currentIndex < songList.size()) {
                current = songList.get(currentIndex);
            }
            Collections.shuffle(songList);
            if (current != null) {
                currentIndex = songList.indexOf(current);
                if (currentIndex == -1) currentIndex = 0;
            }
            Toast.makeText(this, "🔀 Перемешивание включено", Toast.LENGTH_SHORT).show();
        } else {
            Song current = null;
            if (currentIndex >= 0 && currentIndex < songList.size()) {
                current = songList.get(currentIndex);
            }
            songList = new ArrayList<>(originalSongList);
            if (current != null) {
                currentIndex = songList.indexOf(current);
                if (currentIndex == -1) currentIndex = 0;
            }
            Toast.makeText(this, "🔀 Перемешивание выключено", Toast.LENGTH_SHORT).show();
        }
        sendUpdateBroadcast();
    }

    public void toggleRepeat() {
        repeatMode = (repeatMode + 1) % 3;
        String mode = repeatMode == REPEAT_NONE ? "🔁 Повтор выключен" :
                repeatMode == REPEAT_ONE ? "🔂 Повтор одного трека" :
                        "🔁 Повтор всего плейлиста";
        Toast.makeText(this, mode, Toast.LENGTH_SHORT).show();
        sendUpdateBroadcast();
    }

    public int getRepeatMode() { return repeatMode; }
    public boolean isShuffleEnabled() { return shuffleEnabled; }
    public boolean isPlaying() { return isPlaying; }
    public List<Song> getSongList() { return songList; }
    public int getCurrentIndex() { return currentIndex; }

    public Song getCurrentSong() {
        if (currentIndex >= 0 && currentIndex < songList.size()) {
            return songList.get(currentIndex);
        }
        return null;
    }

    public int getCurrentPosition() {
        return exoPlayer != null ? (int) exoPlayer.getCurrentPosition() : 0;
    }

    public int getDuration() {
        return exoPlayer != null ? (int) exoPlayer.getDuration() : 0;
    }

    public void seekTo(int position) {
        if (exoPlayer != null) exoPlayer.seekTo(position);
    }

    // ===== ПЛЕЙЛИСТЫ =====

    public void createPlaylist(String name) {
        Playlist playlist = new Playlist(name);
        playlists.add(playlist);
        savePlaylists();
        Toast.makeText(this, "📁 Плейлист \"" + name + "\" создан", Toast.LENGTH_SHORT).show();
        sendUpdateBroadcast();
    }

    public void deletePlaylist(int index) {
        if (index >= 0 && index < playlists.size()) {
            String name = playlists.get(index).name;
            playlists.remove(index);
            savePlaylists();
            Toast.makeText(this, "🗑️ Плейлист \"" + name + "\" удалён", Toast.LENGTH_SHORT).show();
            sendUpdateBroadcast();
        }
    }

    public void addToPlaylist(int playlistIndex, int songIndex) {
        if (playlistIndex >= 0 && playlistIndex < playlists.size() &&
                songIndex >= 0 && songIndex < songList.size()) {
            Song song = songList.get(songIndex);
            Song copy = new Song(song.name, song.artist, song.path, song.duration, song.genre, song.id);
            playlists.get(playlistIndex).addSong(copy);
            savePlaylists();
            Toast.makeText(this, "✅ Добавлено в плейлист", Toast.LENGTH_SHORT).show();
            sendUpdateBroadcast();
        }
    }

    public void removeFromPlaylist(int playlistIndex, int songIndex) {
        if (playlistIndex >= 0 && playlistIndex < playlists.size()) {
            playlists.get(playlistIndex).removeSong(songIndex);
            savePlaylists();
            Toast.makeText(this, "🗑️ Удалено из плейлиста", Toast.LENGTH_SHORT).show();
            sendUpdateBroadcast();
        }
    }

    public List<Playlist> getPlaylists() { return playlists; }

    public List<Song> getPlaylistSongs(int index) {
        if (index >= 0 && index < playlists.size()) {
            return playlists.get(index).songs;
        }
        return new ArrayList<>();
    }

    public void playPlaylist(int playlistIndex) {
        if (playlistIndex >= 0 && playlistIndex < playlists.size()) {
            Playlist playlist = playlists.get(playlistIndex);
            if (playlist.size() > 0) {
                songList = new ArrayList<>(playlist.songs);
                currentIndex = 0;
                isPlaylistMode = true;
                playSong(0);
                Toast.makeText(this, "▶️ Играет плейлист: " + playlist.name, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "❌ Плейлист пуст", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void exitPlaylistMode() {
        if (isPlaylistMode) {
            songList = new ArrayList<>(originalSongList);
            isPlaylistMode = false;
            Toast.makeText(this, "↩️ Вышли из плейлиста", Toast.LENGTH_SHORT).show();
            sendUpdateBroadcast();
        }
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        if (playbackState == Player.STATE_ENDED) {
            next();
        }
    }

    @Override
    public void onPlayerError(PlaybackException error) {
        Log.e(TAG, "ExoPlayer ошибка: " + error.getMessage());
        handler.post(() -> Toast.makeText(this, "❌ Ошибка воспроизведения", Toast.LENGTH_SHORT).show());
        isPreparing = false;
        isPlaying = false;
        updateNotification();
    }

    // ============================================================
    // ===== НОТИФИКАЦИЯ (ШТОРКА) =====
    // ============================================================

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "music_channel",
                    "Музыка",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Управление музыкой");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        if (currentIndex < 0 || currentIndex >= songList.size()) {
            return null;
        }

        Song song = songList.get(currentIndex);

        // Открытие приложения
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // ===== КНОПКИ ДЛЯ ШТОРКИ (ЧЕРЕЗ BROADCAST) =====
        Intent prevIntent = new Intent(this, NotificationReceiver.class);
        prevIntent.setAction(ACTION_PREV);
        PendingIntent prevPendingIntent = PendingIntent.getBroadcast(
                this, 1, prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent playPauseIntent = new Intent(this, NotificationReceiver.class);
        playPauseIntent.setAction(isPlaying ? ACTION_PAUSE : ACTION_PLAY);
        PendingIntent playPausePendingIntent = PendingIntent.getBroadcast(
                this, 2, playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent nextIntent = new Intent(this, NotificationReceiver.class);
        nextIntent.setAction(ACTION_NEXT);
        PendingIntent nextPendingIntent = PendingIntent.getBroadcast(
                this, 3, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, NotificationReceiver.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(
                this, 4, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "music_channel")
                .setContentTitle(song.name)
                .setContentText(song.artist)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(openPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(isPlaying)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        // Добавляем кнопки
        builder.addAction(android.R.drawable.ic_media_previous, "Назад", prevPendingIntent);

        if (isPlaying) {
            builder.addAction(android.R.drawable.ic_media_pause, "Пауза", playPausePendingIntent);
        } else {
            builder.addAction(android.R.drawable.ic_media_play, "Воспроизвести", playPausePendingIntent);
        }

        builder.addAction(android.R.drawable.ic_media_next, "Вперед", nextPendingIntent);

        return builder.build();
    }

    private void updateNotification() {
        Notification notification = buildNotification();
        if (notification != null) {
            startForeground(1, notification);
        }
        sendUpdateBroadcast();
    }

    private void sendUpdateBroadcast() {
        Intent intent = new Intent(ACTION_UPDATE);
        if (currentIndex >= 0 && currentIndex < songList.size()) {
            Song song = songList.get(currentIndex);
            if (song != null) {
                intent.putExtra("songName", song.name);
                intent.putExtra("artist", song.artist);
                intent.putExtra("position", currentIndex);
                intent.putExtra("isPlaying", isPlaying);
                intent.putExtra("repeatMode", repeatMode);
                intent.putExtra("shuffleEnabled", shuffleEnabled);
            }
        }
        sendBroadcast(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            Log.d(TAG, "📩 onStartCommand: " + action);

            switch (action) {
                case ACTION_PLAY:
                    play();
                    break;
                case ACTION_PAUSE:
                    pause();
                    break;
                case ACTION_NEXT:
                    next();
                    break;
                case ACTION_PREV:
                    prev();
                    break;
                case ACTION_STOP:
                    stopSelf();
                    break;
                case ACTION_REPEAT:
                    toggleRepeat();
                    break;
                case ACTION_SHUFFLE:
                    toggleShuffle();
                    break;
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
        handler.removeCallbacksAndMessages(null);
    }
}