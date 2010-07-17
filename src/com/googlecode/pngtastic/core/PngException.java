/*
 * $Id: PngException.java 958 2010-07-14 07:12:55Z ray $
 * $URL: http://svn.kittenmob.com/projects/pngtastic/src/com/googlecode/pngtastic/core/PngException.java $
 */
package com.googlecode.pngtastic.core;

/**
 * Exception type for pngtastic code
 *
 * @author rayvanderborght
 */
@SuppressWarnings("serial")
public class PngException extends Exception
{
    /** */
    public PngException() {  }

    /** */
    public PngException(String message)
    {
        super(message);
    }

    /** */
    public PngException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
