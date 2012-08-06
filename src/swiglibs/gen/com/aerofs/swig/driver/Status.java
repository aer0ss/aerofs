/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 2.0.4
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package com.aerofs.swig.driver;

public final class Status {
  public final static Status OK = new Status("OK");
  public final static Status ERROR_DRIVER_NOT_INSTALLED = new Status("ERROR_DRIVER_NOT_INSTALLED");
  public final static Status ERROR_UNKNOWN = new Status("ERROR_UNKNOWN");
  public final static Status ERROR_CANNOT_MOUNT = new Status("ERROR_CANNOT_MOUNT");
  public final static Status ERROR_INIT = new Status("ERROR_INIT");

  public final int swigValue() {
    return swigValue;
  }

  public String toString() {
    return swigName;
  }

  public static Status swigToEnum(int swigValue) {
    if (swigValue < swigValues.length && swigValue >= 0 && swigValues[swigValue].swigValue == swigValue)
      return swigValues[swigValue];
    for (int i = 0; i < swigValues.length; i++)
      if (swigValues[i].swigValue == swigValue)
        return swigValues[i];
    throw new IllegalArgumentException("No enum " + Status.class + " with value " + swigValue);
  }

  private Status(String swigName) {
    this.swigName = swigName;
    this.swigValue = swigNext++;
  }

  private Status(String swigName, int swigValue) {
    this.swigName = swigName;
    this.swigValue = swigValue;
    swigNext = swigValue+1;
  }

  private Status(String swigName, Status swigEnum) {
    this.swigName = swigName;
    this.swigValue = swigEnum.swigValue;
    swigNext = this.swigValue+1;
  }

  private static Status[] swigValues = { OK, ERROR_DRIVER_NOT_INSTALLED, ERROR_UNKNOWN, ERROR_CANNOT_MOUNT, ERROR_INIT };
  private static int swigNext = 0;
  private final int swigValue;
  private final String swigName;
}

