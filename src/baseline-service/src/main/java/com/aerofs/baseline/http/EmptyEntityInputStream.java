/*
 * Copyright 2015 Air Computing Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aerofs.baseline.http;

import com.google.common.base.Preconditions;
import io.netty.buffer.ByteBuf;

import javax.annotation.concurrent.ThreadSafe;
import java.io.IOException;

@ThreadSafe
final class EmptyEntityInputStream extends ContentInputStream {

    static final EmptyEntityInputStream EMPTY_ENTITY_INPUT_STREAM = new EmptyEntityInputStream();

    @Override
    public int read() throws IOException {
        return -1;
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public void addBuffer(ByteBuf content, boolean last) throws IOException {
        Preconditions.checkArgument(last && (content.readableBytes() == 0), "cannot add content to an empty entity input stream");
    }
}