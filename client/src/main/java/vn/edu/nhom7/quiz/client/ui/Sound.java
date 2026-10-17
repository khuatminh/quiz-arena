package vn.edu.nhom7.quiz.client.ui;

import java.util.EnumMap;
import java.util.Map;
import java.util.prefs.Preferences;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

/**
 * Game sounds and background music. Silent until {@link #enable()} is called by the real client, so
 * tests and headless renders never touch the audio stack; any media failure just means silence.
 */
public final class Sound {
  public enum Effect {
    TICK("tick", 0.55),
    CORRECT("correct", 0.7),
    WRONG("wrong", 0.6),
    INVITE("invite", 0.8),
    STREAK("streak", 0.75),
    WIN("win", 0.8),
    LOSE("lose", 0.65);

    final String file;
    final double volume;

    Effect(String file, double volume) {
      this.file = file;
      this.volume = volume;
    }
  }

  private static final double MUSIC_VOLUME = 0.22;
  private static final Map<Effect, AudioClip> clips = new EnumMap<>(Effect.class);
  private static Preferences prefs;
  private static boolean enabled, effectsOn = true, musicOn = true, musicWanted;
  private static MediaPlayer music;

  private Sound() {}

  /** Called once from the desktop client; loads clips in the background. */
  public static synchronized void enable() {
    if (enabled || Boolean.getBoolean("quiz.mute")) return;
    enabled = true;
    try {
      prefs = Preferences.userNodeForPackage(Sound.class);
      effectsOn = prefs.getBoolean("effects", true);
      musicOn = prefs.getBoolean("music", true);
    } catch (Throwable ignored) {
      prefs = null;
    }
    var loader =
        new Thread(
            () -> {
              for (var effect : Effect.values()) clip(effect);
            },
            "sound-preload");
    loader.setDaemon(true);
    loader.start();
  }

  private static synchronized AudioClip clip(Effect effect) {
    if (!enabled) return null;
    if (clips.containsKey(effect)) return clips.get(effect);
    AudioClip clip = null;
    try {
      var url = Sound.class.getResource("/assets/sounds/" + effect.file + ".wav");
      if (url != null) {
        clip = new AudioClip(url.toExternalForm());
        clip.setVolume(effect.volume);
      }
    } catch (Throwable ignored) {
      // No audio device or media support: play nothing.
    }
    clips.put(effect, clip);
    return clip;
  }

  public static void play(Effect effect) {
    if (!enabled || !effectsOn) return;
    var clip = clip(effect);
    if (clip == null) return;
    try {
      clip.play();
    } catch (Throwable ignored) {
    }
  }

  /** Background music loops while signed in; {@code false} pauses it. */
  public static synchronized void music(boolean playing) {
    musicWanted = playing;
    if (!enabled) return;
    try {
      if (playing && musicOn) {
        if (music == null) {
          var url = Sound.class.getResource("/assets/sounds/music.wav");
          if (url == null) return;
          music = new MediaPlayer(new Media(url.toExternalForm()));
          music.setCycleCount(MediaPlayer.INDEFINITE);
          music.setVolume(MUSIC_VOLUME);
        }
        music.play();
      } else if (music != null) music.pause();
    } catch (Throwable ignored) {
      music = null;
    }
  }

  public static boolean available() {
    return enabled;
  }

  public static boolean effectsOn() {
    return effectsOn;
  }

  public static boolean musicOn() {
    return musicOn;
  }

  public static void setEffectsOn(boolean on) {
    effectsOn = on;
    save("effects", on);
  }

  public static synchronized void setMusicOn(boolean on) {
    musicOn = on;
    save("music", on);
    music(musicWanted);
  }

  private static void save(String key, boolean value) {
    if (prefs == null) return;
    try {
      prefs.putBoolean(key, value);
    } catch (Throwable ignored) {
    }
  }
}
