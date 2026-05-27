package com.xmen.service;

import com.xmen.config.ThemeCatalog;
import com.xmen.config.ThemeCatalog.Theme;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Read / write access to the live {@link ThemeCatalog}.
 *
 * <p>The Settings API and the JavaFX settings dialog call this service to enumerate themes and
 * switch the active one. Changing the active theme just updates {@code default-id} on the
 * shared catalogue; clients re-fetch the resolved theme for the new palette.
 */
@Slf4j
@Service
public class ThemeService {

  private final ThemeCatalog catalog;
  private final org.springframework.beans.factory.ObjectProvider<SettingsStore> storeProvider;

  @Autowired
  public ThemeService(
      ThemeCatalog catalog,
      org.springframework.beans.factory.ObjectProvider<SettingsStore> storeProvider) {
    this.catalog = catalog;
    this.storeProvider = storeProvider;
  }

  public List<Theme> list() {
    return catalog.getCatalog();
  }

  public Theme active() {
    return catalog.resolve(catalog.getDefaultId());
  }

  public String activeId() {
    return active().getId();
  }

  /** Switch the active theme by id. Returns the resolved (possibly defaulted) theme. */
  public Theme setActive(String id) {
    Theme resolved = catalog.resolve(id);
    catalog.setDefaultId(resolved.getId());
    log.info("Active theme switched to '{}'.", resolved.getId());
    SettingsStore store = storeProvider.getIfAvailable();
    if (store != null) store.persist();
    return resolved;
  }

  public Theme resolve(String id) {
    return catalog.resolve(id);
  }
}
