package vn.edu.nhom7.quiz.common.assets;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.util.*;

public final class AssetRegistry {
  public record Asset(String id, String kind, String path, String attribution, boolean required) {}

  public record Pack(String assetPackVersion, List<Asset> assets) {}

  private final String version;
  private final Map<String, Asset> assets;

  public AssetRegistry(Pack pack) {
    if (!"1".equals(pack.assetPackVersion()))
      throw new IllegalArgumentException("Unsupported asset pack");
    version = pack.assetPackVersion();
    var map = new LinkedHashMap<String, Asset>();
    for (var a : pack.assets()) {
      if (a.id() == null
          || !a.id().matches("[a-z0-9-]+")
          || a.path() == null
          || !a.path().startsWith("/assets/")
          || a.path().contains("..")
          || a.path().contains(":")
          || a.attribution() == null
          || a.attribution().isBlank()
          || map.putIfAbsent(a.id(), a) != null)
        throw new IllegalArgumentException("Invalid asset registry entry");
    }
    assets = Collections.unmodifiableMap(map);
  }

  public static AssetRegistry loadDefault() {
    try (var input = AssetRegistry.class.getResourceAsStream("/assets/registry.json")) {
      if (input == null) throw new IllegalStateException("Missing asset registry");
      return new AssetRegistry(new ObjectMapper().readValue(input, Pack.class));
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read asset registry", e);
    }
  }

  public String version() {
    return version;
  }

  public String assetPackVersion() {
    return version;
  }

  public boolean contains(String id) {
    return assets.containsKey(id);
  }

  public boolean isAvatar(String id) {
    return assets.containsKey(id) && "avatar".equals(assets.get(id).kind());
  }

  public List<Asset> assets() {
    return List.copyOf(assets.values());
  }

  public Optional<Asset> find(String id) {
    return Optional.ofNullable(assets.get(id));
  }
}
