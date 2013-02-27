package com.aerofs.controller;

import com.aerofs.base.Loggers;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.proto.ControllerProto.ControllerServiceReactor;
import com.aerofs.proto.ControllerNotifications.Type;
import com.aerofs.ui.UI;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.GeneratedMessageLite;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ControllerProgram implements IProgram, IViewNotifier
{
    private static final Logger l = Loggers.getLogger(ControllerProgram.class);

    private static final BlockingQueue<RPCArgs> _queue = new LinkedBlockingQueue<RPCArgs>();
    private ControllerServiceReactor _reactor;

    @Override
    public void launch_(String rtRoot, String prog, String[] args) throws Exception
    {
        assert (rtRoot != null);
        ControllerService.init(rtRoot, this);
        _reactor = new ControllerServiceReactor(ControllerService.get());
        mainLoop();
    }

    /**
     * This is the main RPC processing loop
     * The main controller thread blocks here waiting for RPC from the view
     */
    private void mainLoop()
    {
        while (true) {
            try {
                // Block until we find something on the queue
                final RPCArgs call = _queue.take();
                ListenableFuture<byte[]> reply = _reactor.react(call.getBytes());
                Futures.addCallback(reply, new FutureCallback<byte[]>()
                {
                    @Override
                    public void onSuccess(byte[] bytes)
                    {
                        sendReplyToView(bytes, call.getParam1(), call.getParam2());
                    }

                    @Override
                    public void onFailure(Throwable throwable)
                    {
                        l.warn("The controller reactor throwed an exception: " + Util.e(throwable));
                    }
                });
            } catch (Exception e) {
                l.warn("Exception in controller's main loop: " + Util.e(e));
            }
        }
    }

    /**
     * Holds the data of a pending RPC.
     * The purpose of this class is to hold together the data received from the client.
     *
     * param1 and param2 are opaque 64-bits identifiers used by the client to match the reply with
     * the request. Their exact meaning is view-specific. In Objective-C, they are pointers to
     * a selector and a class instance, while on Qt they point to a slot and a QObject instance.
     */
    private static class RPCArgs
    {
        byte[] _bytes;
        long _param1;
        long _param2;

        RPCArgs(byte[] bytes, long param1, long param2)
        {
            _bytes = bytes;
            _param1 = param1;
            _param2 = param2;
        }

        byte[] getBytes() { return _bytes; }
        long getParam1() { return _param1; }
        long getParam2() { return _param2; }
    }

    @Override
    public void notify(Type type, @Nullable GeneratedMessageLite notification)
    {
        // Notify the view
        byte[] data = (notification != null) ? notification.toByteArray() : null;
        sendNotificationToView(type.getNumber(), data);

        // We also need to send the notification to the Java part of the code base, because some
        // subsystem might be listening to them (eg: shellext's FileStatusNotifier)
        UI.notifier().notify(type, notification);
    }

    ////////////////////////////////////
    ////////////////////////////////////
    // JNI INTERFACE

    /**
     * Called by the view with a RPC request
     * This method is thread-safe
     * @param request the request
     * @param param1 opaque request identifier. The controller must send it back with the reply.
     * @param param2 opaque request identifier. The controller must send it back with the reply.
     * Use sendReplyToView() to send the reply along with param1 and param2.
     */
    public static void onRequestFromView(byte[] request, long param1, long param2)
    {
        RPCArgs call = new RPCArgs(request, param1, param2);
        try {
            _queue.put(call);
        } catch (InterruptedException e) {
            SystemUtil.fatal(e);
        }
    }

    /**
     * Native method implemented by the view
     * @param reply the reply
     * @param param1 the value that was supplied by the view for the request
     * @param param2 the value that was supplied by the view for the request
     */
    public static native void sendReplyToView(byte[] reply, long param1, long param2);

    /**
     * Sends a notification to the native UI
     * This method can be called from any thread, and the view must ensure thread safety
     */
    public static native void sendNotificationToView(int type, byte[] notification);
}
