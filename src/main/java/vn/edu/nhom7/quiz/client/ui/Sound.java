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
    LIFELINE("lifeline", 0.75),
    WIN("win", 0.8),
    LOSE("lose", 0.65);

    final String file;
    final double volume;

    Effect(String file, double volume) {
      this.file = file;
      this.volume = volume;
    }
  }

  /** Loudest music volume; the default level of 40% gives the original 0.22. */
  private static final double MUSIC_VOLUME = 0.55;

  /** Effect volumes are tuned for the default level of 80%. */
  private static final double EFFECTS_DEFAULT = 0.8;

  private static final Map<Effect, AudioClip> clips = new EnumMap<>(Effect.class);
  private static Preferences prefs;
  private static boolean enabled, effectsOn = true, musicOn = true, musicWanted;
  private static MediaPlayer music;
  private static double musicLevel = 0.4, effectsLevel = EFFECTS_DEFAULT;

  private Sound() {}

  /** Called once from the desktop client; loads clips in the background. */
  public static synchronized void enable() {
    if (enabled || Boolean.getBoolean("quiz.mute")) return;
    enabled = true;
    try {
      prefs = Preferences.userNodeForPackage(Sound.class);
      effectsOn = prefs.getBoolean("effects", true);
      musicOn = prefs.getBoolean("music", true);
      musicLevel = clamp(prefs.getDouble("musicLevel", musicLevel));
      effectsLevel = clamp(prefs.getDouble("effectsLevel", effectsLevel));
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
      clip.play(Math.min(1, effect.volume * effectsLevel / EFFECTS_DEFAULT));
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
          music.setVolume(MUSIC_VOLUME * musicLevel);
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

  /** Music loudness from 0 to 1, shown as a percentage in the settings. */
  public static double musicLevel() {
    return musicLevel;
  }

  public static double effectsLevel() {
    return effectsLevel;
  }

  public static synchronized void setMusicLevel(double level) {
    musicLevel = clamp(level);
    save("musicLevel", musicLevel);
    try {
      if (music != null) music.setVolume(MUSIC_VOLUME * musicLevel);
    } catch (Throwable ignored) {
    }
  }

  public static void setEffectsLevel(double level) {
    effectsLevel = clamp(level);
    save("effectsLevel", effectsLevel);
  }

  private static double clamp(double level) {
    return Double.isNaN(level) ? 0 : Math.max(0, Math.min(1, level));
  }

  private static void save(String key, double value) {
    if (prefs == null) return;
    try {
      prefs.putDouble(key, value);
    } catch (Throwable ignored) {
    }
  }

  private static void save(String key, boolean value) {
    if (prefs == null) return;
    try {
      prefs.putBoolean(key, value);
    } catch (Throwable ignored) {
    }
  }
}
