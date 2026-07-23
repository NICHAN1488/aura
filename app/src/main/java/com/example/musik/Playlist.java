package com.example.musik;

import java.util.ArrayList;
import java.util.List;

public class Playlist {
    public String name;
    public List<Song> songs;

    public Playlist(String name) {
        this.name = name;
        this.songs = new ArrayList<>();
    }

    public void addSong(Song song) {
        songs.add(song);
    }

    public void removeSong(int index) {
        if (index >= 0 && index < songs.size()) {
            songs.remove(index);
        }
    }

    public void clear() {
        songs.clear();
    }

    public int size() {
        return songs.size();
    }

    public Song getSong(int index) {
        if (index >= 0 && index < songs.size()) {
            return songs.get(index);
        }
        return null;
    }
}