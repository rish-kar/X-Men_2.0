package com.xmen.service;

import com.xmen.config.CeremonyVocabulary;
import com.xmen.config.ThemeCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ThemePersistenceTest {
  @TempDir Path temporary;

  private ThemeCatalog catalog() {
    ThemeCatalog catalog = new ThemeCatalog();
    ThemeCatalog.Theme light = new ThemeCatalog.Theme();
    light.setId("paper-light");
    catalog.setCatalog(List.of(new ThemeCatalog.Theme(), light));
    return catalog;
  }

  private ThemeService service(ThemeCatalog catalog, SettingsStore store) {
    StaticListableBeanFactory beans = new StaticListableBeanFactory();
    beans.addBean("settingsStore", store);
    return new ThemeService(catalog, beans.getBeanProvider(SettingsStore.class));
  }

  @Test void savedLightThemeSurvivesRestart() throws Exception {
    Path file = temporary.resolve("settings.json");
    ThemeCatalog catalog = catalog();
    SettingsStore store = new SettingsStore(new CeremonyVocabulary(), catalog, file);
    service(catalog, store).setActive("paper-light");
    ThemeCatalog restarted = catalog();
    new SettingsStore(new CeremonyVocabulary(), restarted, file).loadFromDisk();
    assertEquals("paper-light", restarted.getDefaultId());
    try (var files = Files.list(temporary)) { assertEquals(1, files.count()); }
  }

  @Test void failedSaveIsReportedAndDoesNotChangeActiveTheme() throws Exception {
    Path file = temporary.resolve("settings.json");
    Files.createDirectory(file);
    Files.writeString(file.resolve("keep"), "existing data");
    ThemeCatalog catalog = catalog();
    SettingsStore store = new SettingsStore(new CeremonyVocabulary(), catalog, file);
    assertThrows(UncheckedIOException.class, () -> service(catalog, store).setActive("paper-light"));
    assertEquals("classic", catalog.getDefaultId());
    assertEquals("existing data", Files.readString(file.resolve("keep")));
    try (var files = Files.list(temporary)) { assertEquals(1, files.count()); }
  }
}
