/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 2.0.8
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package com.aerofs.swig.driver;

public final class LogLevel {
  public final static LogLevel LDEBUG = new LogLevel("LDEBUG", DriverJNI.LDEBUG_get());
  public final static LogLevel LINFO = new LogLevel("LINFO");
  public final static LogLevel LWARN = new LogLevel("LWARN");
  public final static LogLevel LERROR = new LogLevel("LERROR");

  public final int swigValue() {
    return swigValue;
  }

  public String toString() {
    return swigName;
  }

  public static LogLevel swigToEnum(int swigValue) {
    if (swigValue < swigValues.length && swigValue >= 0 && swigValues[swigValue].swigValue == swigValue)
      return swigValues[swigValue];
    for (int i = 0; i < swigValues.length; i++)
      if (swigValues[i].swigValue == swigValue)
        return swigValues[i];
    throw new IllegalArgumentException("No enum " + LogLevel.class + " with value " + swigValue);
  }

  private LogLevel(String swigName) {
    this.swigName = swigName;
    this.swigValue = swigNext++;
  }

  private LogLevel(String swigName, int swigValue) {
    this.swigName = swigName;
    this.swigValue = swigValue;
    swigNext = swigValue+1;
  }

  private LogLevel(String swigName, LogLevel swigEnum) {
    this.swigName = swigName;
    this.swigValue = swigEnum.swigValue;
    swigNext = this.swigValue+1;
  }

  private static LogLevel[] swigValues = { LDEBUG, LINFO, LWARN, LERROR };
  private static int swigNext = 0;
  private final int swigValue;
  private final String swigName;
}

