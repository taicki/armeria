/*
 * Copyright 2015 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.linecorp.armeria.common.http;

import static io.netty.handler.codec.http2.Http2Error.INTERNAL_ERROR;

import com.linecorp.armeria.common.util.Exceptions;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Exception.ClosedStreamCreationException;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2Stream.State;
import io.netty.handler.codec.http2.Http2StreamVisitor;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler;

public abstract class AbstractHttpToHttp2ConnectionHandler extends HttpToHttp2ConnectionHandler {

    /**
     * XXX(trustin): Don't know why, but {@link Http2ConnectionHandler} does not close the last stream
     *               on a cleartext connection, so we make sure all streams are closed.
     */
    private static final Http2StreamVisitor closeAllStreams = stream -> {
        if (stream.state() != State.CLOSED) {
            stream.close();
        }
        return true;
    };

    private boolean closing;
    private boolean handlingConnectionError;

    protected AbstractHttpToHttp2ConnectionHandler(
            Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
            Http2Settings initialSettings, boolean validateHeaders) {

        super(decoder, encoder, initialSettings, validateHeaders);
    }

    public boolean isClosing() {
        return closing;
    }

    @Override
    protected void onConnectionError(ChannelHandlerContext ctx, Throwable cause, Http2Exception http2Ex) {
        if (handlingConnectionError) {
            return;
        }

        handlingConnectionError = true;

        // TODO(trustin): Remove this once Http2ConnectionHandler.goAway() sends better debugData.
        //                See https://github.com/netty/netty/issues/5160
        if (http2Ex == null) {
            http2Ex = new Http2Exception(INTERNAL_ERROR, goAwayDebugData(null, cause), cause);
        } else if (http2Ex instanceof ClosedStreamCreationException) {
            final ClosedStreamCreationException e = (ClosedStreamCreationException) http2Ex;
            http2Ex = new ClosedStreamCreationException(e.error(), goAwayDebugData(e, cause), cause);
        } else {
            http2Ex = new Http2Exception(
                    http2Ex.error(), goAwayDebugData(http2Ex, cause), cause, http2Ex.shutdownHint());
        }

        super.onConnectionError(ctx, cause, http2Ex);
    }

    private static String goAwayDebugData(Http2Exception http2Ex, Throwable cause) {
        final StringBuilder buf = new StringBuilder(256);
        final String type;
        final String message;

        if (http2Ex != null) {
            type = http2Ex.getClass().getName();
            message = http2Ex.getMessage();
        } else {
            type = null;
            message = null;
        }

        buf.append("type: ");
        buf.append(type != null ? type : "n/a");
        buf.append(", message: ");
        buf.append(message != null ? message : "n/a");
        buf.append(", cause: ");
        buf.append(cause != null ? Exceptions.traceText(cause) : "n/a");

        return buf.toString();
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        closing = true;

        // TODO(trustin): Remove this line once https://github.com/netty/netty/issues/4210 is fixed.
        connection().forEachActiveStream(closeAllStreams);

        onCloseRequest(ctx);
        super.close(ctx, promise);
    }

    protected abstract void onCloseRequest(ChannelHandlerContext ctx) throws Exception;
}
