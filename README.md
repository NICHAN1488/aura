<div align="center">

# 🎵 AURA Music Player

### Listen to your music. Anywhere. Anytime.

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Java](https://img.shields.io/badge/Java-8+-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.java.com/)
[![ExoPlayer](https://img.shields.io/badge/ExoPlayer-2.19.1-FF6F00?style=for-the-badge&logo=android&logoColor=white)](https://exoplayer.dev/)
[![Navidrome](https://img.shields.io/badge/Navidrome-0.63.2-1DB954?style=for-the-badge&logo=music&logoColor=white)](https://www.navidrome.org/)
[![License](https://img.shields.io/badge/License-MIT-1DB954?style=for-the-badge)](LICENSE)

---

**AURA** — это минималистичный аудиоплеер для Android, который подключается к вашему Navidrome-серверу и позволяет слушать музыку из любой точки мира.

---

## ✨ Возможности

| 🎵 | 📱 | 🔍 |
|:---:|:---:|:---:|
| **Streaming** | **Notification Controls** | **Search** |
| Воспроизведение с Navidrome | Управление из шторки уведомлений | Поиск по трекам и исполнителям |

| 🏷️ | 📁 | 🔀 |
|:---:|:---:|:---:|
| **Genre Filter** | **Playlists** | **Shuffle & Repeat** |
| Фильтрация по жанрам | Создание и управление плейлистами | Перемешивание и повтор |

| ⏪ | 🎨 | 🌐 |
|:---:|:---:|:---:|
| **SeekBar** | **Dark Theme** | **Cloudflare Tunnel** |
| Перемотка по треку | Тёмная тема с акцентом #1DB954 | Доступ из любой точки мира |

---

## 🛠️ Технологии

| Компонент | Описание |
|-----------|----------|
| **Язык** | Java 8 |
| **Плеер** | ExoPlayer от Google |
| **Сервер** | Navidrome (Subsonic API) |
| **Туннель** | Cloudflare Tunnel |
| **UI** | XML + Material Design |

---

## 🔄 Как это работает (Technical Flow)

### Архитектура приложения

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              ПОТОК ДАННЫХ В AURA                           │
└─────────────────────────────────────────────────────────────────────────────┘

    ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
    │    ТЕЛЕФОН   │     │   CLOUDFLARE │     │    СЕРВЕР    │
    │   (Плеер)    │ ◄──► │    TUNNEL    │ ◄──► │  (Navidrome)│
    └──────────────┘     └──────────────┘     └──────────────┘
          │                      │                     │
          ▼                      ▼                     ▼
    ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
    │  ExoPlayer   │     │  trycloudflare│     │  Папка music/│
    │  (аудио)     │     │  .com/xxx    │     │  с MP3-файлами│
    └──────────────┘     └──────────────┘     └──────────────┘
```

---

## 📡 Полный цикл запроса (User Flow)

### 1️⃣ Загрузка списка треков

**Что делает пользователь:** Открывает приложение

**Что происходит в коде:**
MusicService.loadFromNavidrome()
↓
GET /rest/getAlbumList2?u=NICHAN&p=&type=random&size=50
↓
Сервер возвращает JSON с альбомами
↓
Для каждого альбома делается запрос:
GET /rest/getAlbum?id=XXX
↓
Получаем список треков с их ID
↓
Для каждого трека формируем ссылку на стриминг:
/rest/stream?id=XXX&u=NICHAN&p=
↓
Сохраняем в List<Song>
↓
Отправляем Broadcast (ACTION_UPDATE)
↓
MainActivity обновляет UI

text

**Пример JSON-ответа от Navidrome:**
```json
{
  "subsonic-response": {
    "status": "ok",
    "albumList2": {
      "album": [
        {
          "id": "7l8CH1UBQL5VXtxQFrHDBr",
          "name": "Осторожно Окрашено",
          "artist": "Каспийский Груз",
          "songCount": 1
        }
      ]
    }
  }
}
2️⃣ Воспроизведение трека
Что делает пользователь: Нажимает на трек в списке

Что происходит в коде:

text
MainActivity.onItemClick()
    ↓
musicService.playSong(index)
    ↓
Берём Song.path (URL стриминга)
    ↓
ExoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
    ↓
ExoPlayer.prepare() → подготавливает аудио
    ↓
ExoPlayer.setPlayWhenReady(true) → начинает играть
    ↓
updateNotification() → появляется в шторке
    ↓
sendUpdateBroadcast() → обновляет мини-плеер
Пример URL для стриминга:

text
https://xxx.trycloudflare.com/rest/stream?id=XXX&u=NICHAN&p=***&v=1.16.1&c=myplayer
3️⃣ Управление из шторки уведомлений
Что делает пользователь: Свайпает вниз → нажимает кнопку в уведомлении

Что происходит в коде:

text
Пользователь нажал "Пауза" в шторке
    ↓
NotificationReceiver.onReceive()
    ↓
Получает ACTION_PAUSE
    ↓
context.startService(Intent(ACTION_PAUSE))
    ↓
MusicService.onStartCommand()
    ↓
pause() → exoPlayer.setPlayWhenReady(false)
    ↓
updateNotification() → меняем иконку на "Плей"
    ↓
sendUpdateBroadcast() → обновляем UI
Цепочка Intent'ов:

text
Notification (кнопка)
    ↓
PendingIntent.getBroadcast()
    ↓
NotificationReceiver (ловит действие)
    ↓
MusicService (выполняет действие)
    ↓
UI обновляется
4️⃣ Перемотка трека (SeekBar)
Что делает пользователь: Тянет ползунок в мини-плеере

Что происходит в коде:

text
Пользователь двигает SeekBar
    ↓
SeekBar.OnSeekBarChangeListener.onProgressChanged()
    ↓
musicService.seekTo(progress)
    ↓
exoPlayer.seekTo(position)
    ↓
miniCurrentTime.setText(formatTime(progress))
text

---

## 🚀 КАК ЗАМЕНИТЬ:

1. **Найди в README.md раздел `## Полный цикл запроса (User Flow)`**
2. **Удали ВСЁ оттуда до следующего раздела**
3. **Вставь этот новый текст**
4. **Сохрани**
5. **Заливай**:

```bash
git add README.md
git commit -m "Починил форматирование User Flow"
git push

## 🗺️ Схема данных

### Модель трека (Song.java)
public class Song {
    String name;        // Название трека
    String artist;      // Исполнитель
    String path;        // URL для стриминга
    int duration;       // Длительность в мс
    String genre;       // Жанр
    String id;          // Уникальный ID на сервере
}

### Модель плейлиста (Playlist.java)
public class Playlist {
    String name;           // Название плейлиста
    List<Song> songs;      // Список треков
}

---

## 🔒 Безопасность и авторизация

1. Авторизация в Navidrome — логин и пароль передаются в каждом запросе: ?u=NICHAN&p=Sosilol123
2. Cloudflare Tunnel — создаёт зашифрованный туннель через HTTPS
3. Данные не хранятся — приложение не сохраняет музыку на устройство

---

## 🌐 Сеть и туннели

### Как работает Cloudflare Tunnel (бесплатный вариант)

**БЕЗ ТУННЕЛЯ (не работает из интернета):**

```
┌────────────┐     ┌────────────┐
│  Телефон   │  ✗  │  Сервер    │
│            │     │ 192.168.1  │
└────────────┘     └────────────┘
```

**С ТУННЕЛЕМ (работает из любой точки):**

```
┌────────────┐     ┌──────────────┐     ┌────────────┐
│  Телефон   │ ──► │  Cloudflare  │ ──► │  Сервер    │
│  (Интернет)│     │  Tunnel      │     │  localhost  │
└────────────┘     └──────────────┘     └────────────┘
```

**Команда для запуска туннеля:**
```bash
cloudflared tunnel --url http://localhost:4533 --protocol http2
```

**Результат:** получаем публичную ссылку:
```
https://xxx-xxx.trycloudflare.com
```

---

## 🔧 Обработка ошибок

| Ошибка | Причина | Решение |
|--------|---------|---------|
| JSONException: Value <!DOCTYPE... | Сервер вернул HTML вместо JSON | Проверить логин/пароль в MusicService |
| Не могу загрузить треки | Navidrome не запущен или туннель упал | Проверить, что оба окна открыты |
| Ошибка 530 | Cloudflare не видит Navidrome | Перезапустить туннель с --protocol http2 |
| Кнопки не работают | Сервис не привязан | Переустановить приложение |

---

## 📊 Логирование

Все логи пишутся в Logcat с тегом MusicService:
MusicService: Запрос: https://xxx.trycloudflare.com/rest/getAlbumList2...
MusicService: Код ответа: 200
MusicService: JSON получен
MusicService: ▶️ Играет: Осторожно Окрашено
MusicService: ➡️ next() вызван, currentIndex=2

**Как включить логи:** Android Studio → Logcat → фильтр MusicService

---

## 📱 Установка

### Способ 1: Скачать APK
1. Перейди в раздел Releases на GitHub
2. Скачай последнюю версию app-debug.apk
3. Установи на Android-устройство

### Способ 2: Собрать из исходников
git clone https://github.com/NICHAN1488/aura.git
cd aura
./gradlew assembleDebug

---

## 🔧 Настройка сервера

1. Установи Navidrome на свой ПК или VPS
2. Запусти Navidrome:
   navidrome.exe --address 0.0.0.0 --port 4533
3. Запусти Cloudflare Tunnel:
   cloudflared tunnel --url http://localhost:4533 --protocol http2
4. Скопируй ссылку типа https://что-то.trycloudflare.com
5. Вставь ссылку в код (MusicService.java):
   private static final String SERVER_URL = "https://что-то.trycloudflare.com";
6. Собери APK и установи на телефон

---

## 📁 Структура проекта

**app/src/main/java/com/example/musik/**
- `MainActivity.java` — Главный экран
- `FullPlayerActivity.java` — Полный плеер
- `MusicService.java` — Сервис воспроизведения
- `NotificationReceiver.java` — Управление из шторки
- `Song.java` — Модель трека
- `Playlist.java` — Модель плейлиста

**app/src/main/res/**
- `layout/` — XML-макеты
- `drawable/` — Иконки и ресурсы
- `values/` — Стили и цвета

**Корневые файлы:**
- `AndroidManifest.xml` — Конфигурация приложения
- `build.gradle` — Сборка проекта
- `README.md` — Описание проекта

---

## 🤝 Как помочь проекту

1. Поставь звёздочку ⭐ на GitHub
2. Форкни репозиторий
3. Создай ветку для фичи
4. Сделай Pull Request

---

## 📝 Лицензия

Проект распространяется под лицензией **MIT** — можно использовать, изменять и распространять.

---

## 👤 Автор

**NICHAN1488**
- GitHub: [@NICHAN1488](https://github.com/NICHAN1488)

---

**Сделано с ❤️ для любителей музыки**

</div>
```
