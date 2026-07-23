package com.example.musik;

public class Song {
    public String name;
    public String artist;
    public String path;
    public long duration;
    public String genre;
    public String id; // для Navidrome

    public Song(String name, String artist, String path, long duration) {
        this.name = name;
        this.artist = artist;
        this.path = path;
        this.duration = duration;
        this.genre = "Поп";
        this.id = "";
    }

    public Song(String name, String artist, String path, long duration, String genre) {
        this.name = name;
        this.artist = artist;
        this.path = path;
        this.duration = duration;
        this.genre = genre;
        this.id = "";
    }

    public Song(String name, String artist, String path, long duration, String genre, String id) {
        this.name = name;
        this.artist = artist;
        this.path = path;
        this.duration = duration;
        this.genre = genre;
        this.id = id;
    }
}