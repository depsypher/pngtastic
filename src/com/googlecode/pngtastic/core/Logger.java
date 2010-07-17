/*
 * $Id$
 * $URL$
 */
package com.googlecode.pngtastic.core;

import java.util.Arrays;
import java.util.List;

/**
 * Custom logger because I want to have zero dependancies; perhaps to be replaced with java.util logging.
 * Made package access to limit its use outside of pngtastic core.
 *
 * @author rayvanderborght
 */
class Logger
{
	/** */
	static final String NONE = "NONE";
	static final String DEBUG = "DEBUG";
	static final String INFO = "INFO";
	static final String ERROR = "ERROR";
	private static final List<String> LOG_LEVELS = Arrays.asList(NONE, DEBUG, INFO, ERROR);

	/** */
	private final String logLevel;

	/** */
	Logger(String logLevel)
	{
		this.logLevel = (logLevel == null || !LOG_LEVELS.contains(logLevel.toUpperCase()))
				? INFO
				: logLevel.toUpperCase();
	}

	/**
	 * Write debug messages.
	 * Takes a varags list of args so that string concatenation only happens if the logging level applies.
	 *
	 * @param messages
	 */
	void debug(String format, Object... args)
	{
		if (DEBUG.equals(this.logLevel))
			System.out.println(String.format(format, args));
	}

	/**
	 * Write info messages.
	 * Takes a varags list of args so that string concatenation only happens if the logging level applies.
	 *
	 * @param messages
	 */
	void info(String format, Object... args)
	{
		if (DEBUG.equals(this.logLevel) || INFO.equals(this.logLevel))
			System.out.println(String.format(format, args));
	}
}
