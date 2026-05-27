package com.xmen.service.impl;

public final class DerivationModeContext {
  private static final ThreadLocal<Boolean> USE_HASKELL = ThreadLocal.withInitial(() -> false);

  private DerivationModeContext() {}

  public static void enableHaskell() {
    USE_HASKELL.set(true);
  }

  public static void disableHaskell() {
    USE_HASKELL.set(false);
  }

  public static boolean isHaskellEnabled() {
    return Boolean.TRUE.equals(USE_HASKELL.get());
  }
}

