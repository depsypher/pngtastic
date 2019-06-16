package com.googlecode.pngtastic.core;

public enum SystemDetector {
	UNIX(),
	MAC(),
	WINDOWS(),
	SOLARIS(),
	UNKNOWN();

	private static SystemDetector OS_CACHE = null;

	public static SystemDetector getOS() {
		if (OS_CACHE == null) {
			if (isWindows()) {
				OS_CACHE = WINDOWS;
			} else if (isMac()) {
				OS_CACHE = MAC;
			} else if (isSolaris()) {
				OS_CACHE = SOLARIS;
			} else if (isUnix()) {
				OS_CACHE = UNIX;
			} else {
				OS_CACHE = UNKNOWN;
			}
		}
		return OS_CACHE;
	}

	private static boolean isWindows() {
		return (System.getProperty("os.name").toLowerCase().contains("win"));
	}

	public static boolean isMac() {
		return (System.getProperty("os.name").toLowerCase().contains("mac"));
	}

	public static boolean isUnix() {
		String OS = System.getProperty("os.name").toLowerCase();
		return (OS.contains("nix") || OS.contains("nux") || OS.contains("aix"));
	}

	public static boolean isSolaris() {
		return (System.getProperty("os.name").toLowerCase().contains("sunos"));
	}
}
