/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.tng.base.pipeline.IConnection;
import com.aerofs.daemon.tng.base.pipeline.IPipeline;
import com.aerofs.daemon.tng.base.pipeline.IPipelineEvent;
import com.aerofs.daemon.tng.ex.ExTransport;
import com.aerofs.lib.async.UncancellableFuture;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;

import javax.annotation.Nullable;

import static com.aerofs.daemon.tng.base.ConnectionEvent.Type.CONNECT;
import static com.aerofs.daemon.tng.base.ConnectionEvent.Type.DISCONNECT;
import static com.aerofs.lib.async.FutureUtil.addCallback;

class PeerConnection implements IConnection
{
    private final ISingleThreadedPrioritizedExecutor _executor;
    private final UncancellableFuture<Void> _closeFuture = UncancellableFuture.createCloseFuture();
    private boolean _startedReceiveLoop = false;

    @Nullable private IPipeline _pipeline;

    static PeerConnection getInstance_(ISingleThreadedPrioritizedExecutor executor,
            IUnicastConnection unicast)
    {
        final PeerConnection connection = new PeerConnection(executor);

        connection._closeFuture.chainException(unicast.getCloseFuture_());
        return connection;
    }

    private PeerConnection(ISingleThreadedPrioritizedExecutor executor)
    {
        this._executor = executor;
    }

    void setPipeline_(IPipeline pipeline)
    {
        assert _pipeline == null;
        _pipeline = pipeline;
    }

    @Override
    public ListenableFuture<Void> getCloseFuture_()
    {
        return _closeFuture;
    }

    @Override
    public ListenableFuture<Void> send_(Object input, Prio pri)
    {
        if (_closeFuture.isDone()) {
            return _closeFuture;
        }

        return send_(new MessageEvent(this, UncancellableFuture.<Void>create(), input, pri));
    }

    ListenableFuture<Void> connect_()
    {
        if (_closeFuture.isDone()) {
            return _closeFuture;
        }

        ListenableFuture<Void> returned = send_(new ConnectionEvent(this, CONNECT, null));
        _closeFuture.chainException(returned);
        return returned;
    }

    @Override
    public ListenableFuture<Void> disconnect_(Exception ex)
    {
        if (!_closeFuture.isDone()) {
            _closeFuture.setException(ex);
            send_(new ConnectionEvent(this, DISCONNECT, ex));
        }

        return _closeFuture;
    }

    private <FutureReturn> ListenableFuture<FutureReturn> send_(IPipelineEvent<FutureReturn> event)
    {
        assert _pipeline != null;

        _pipeline.processOutgoing_(event);
        return event.getCompletionFuture_();
    }

    void startReceiveLoop_()
    {
        if (_closeFuture.isDone()) {
            return;
        }

        assert !_startedReceiveLoop;
        _startedReceiveLoop = true;
        receive_();
    }

    private void receive_()
    {
        addCallback(send_(new ReadEvent(this)), new FutureCallback<ImmutableList<WireData>>()
        {
            @Override
            public void onSuccess(ImmutableList<WireData> dataList)
            {
                for (WireData data : dataList) {
                    _pipeline.processIncoming_(new MessageEvent(PeerConnection.this,
                            UncancellableFuture.<Void>create(), data, Prio.LO));
                }

                receive_();
            }

            @Override
            public void onFailure(Throwable t)
            {
                if (t instanceof Exception) {
                    disconnect_((Exception) t);
                } else {
                    disconnect_(new ExTransport("read failed err:" + t));
                }
            }
        }, _executor);
    }
}
