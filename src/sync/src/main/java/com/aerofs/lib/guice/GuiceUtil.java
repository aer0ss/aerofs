package com.aerofs.lib.guice;

import com.google.inject.Binder;
import com.google.inject.multibindings.Multibinder;

public class GuiceUtil
{
//    private static Handler _handler;
//
//    private static void initHanlder()
//    {
//
//        _handler = new StreamHandler(System.out, new Formatter() {
//            @Override
//            public String format(LogRecord record) {
//                return String.format("[Guice %s] %s%n",
//                        record.getLevel().getName(),
//                        record.getMessage());
//            }
//        });
//        _handler.setLevel(Level.ALL);
//    }
//
//    private static Logger getLogger()
//    {
//        return Logger.getLogger("com.google.inject");
//    }
//
//    public static void enableLogging()
//    {
//        if (_handler == null) initHanlder();
//        Logger guiceLogger = getLogger();
//        guiceLogger.addHandler(_handler);
//        guiceLogger.setLevel(Level.ALL);
//    }
//
//    public static void disableLogging()
//    {
//        assert _handler != null;
//
//        Logger guiceLogger = getLogger();
//        guiceLogger.setLevel(Level.OFF);
//        guiceLogger.removeHandler(_handler);
//    }

    /**
     * Multi-bind {@code impl} to {@code type}. Use this method in AbstractModule.configure(), and
     * pass AbstarctModule.binder() as the parameter {@code binder}.
     */
    public static <T> void multibind(Binder binder, Class<T> type, Class<? extends T> impl)
    {
        Multibinder.newSetBinder(binder, type).addBinding().to(impl);
    }
}
