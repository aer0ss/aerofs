package com.aerofs.lib.raven;

public class RavenTraceElement {
    String _className;
    String _methodName;
    String _lineNumber;

    public String getClassName() {
        return _className;
    }

    public void setClassName(String className) {
        _className = className;
    }

    public String getMethodName() {
        return _methodName;
    }

    public void setMethodName(String methodName) {
        _methodName = methodName;
    }

    public String getLineNumber() {
        return _lineNumber;
    }

    public void setLineNumber(String lineNumber) {
        _lineNumber = lineNumber;
    }

    public RavenTraceElement(String className, String methodName, String lineNumber)
    {
        _className = className;
        _methodName = methodName;
        _lineNumber = lineNumber;
    }

    @Override
    public String toString() {
        return "className: " + _className + " | methodName: " + _methodName + " | lineNumber: " + _lineNumber;
    }
}
