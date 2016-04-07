/**
 * Copyright (C) 2010-2013 Alibaba Group Holding Limited
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.rocketmq.remoting.netty;

import com.alibaba.rocketmq.common.protocol.protobuf.Command.MessageCommand;
import com.alibaba.rocketmq.remoting.common.RemotingHelper;
import com.alibaba.rocketmq.remoting.common.RemotingUtil;
import com.google.protobuf.ByteString;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;


/**
 * @author shijia.wxr<vintage.wang@gmail.com>
 * @since 2013-7-13
 */
public class NettyDecoder extends LengthFieldBasedFrameDecoder {
    private static final Logger log = LoggerFactory.getLogger(RemotingHelper.RemotingLogName);
    private static final int FRAME_MAX_LENGTH = //
            Integer.parseInt(System.getProperty("com.rocketmq.remoting.frameMaxLength", "8388608"));

    private MessageCommand messageCommand = MessageCommand.getDefaultInstance();

    public NettyDecoder() {
        super(FRAME_MAX_LENGTH, 0, 4, 0, 4);
    }


    @Override
    public Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf frame = null;
        try {
            frame = (ByteBuf) super.decode(ctx, in);
            if (null == frame) {
                return null;
            }

            ByteBuffer byteBuffer = frame.nioBuffer();
            int total = byteBuffer.limit();
            boolean sendFileFlag = byteBuffer.getInt() == Integer.MAX_VALUE ? true : false;
            if (sendFileFlag) {
                int protobufPartLen = byteBuffer.getInt();
                byte[] protobufData = new byte[protobufPartLen];
                byteBuffer.get(protobufData);
                MessageCommand command = messageCommand.getParserForType().parseFrom(protobufData);

                int bodyLen = total - 4 - 4 - protobufPartLen;

                if (bodyLen <= 0) {
                    return command;
                }

                byte[] body = new byte[bodyLen];
                byteBuffer.get(body);

                return MessageCommand.newBuilder(command).setBody(ByteString.copyFrom(body)).build();
            } else {
                byteBuffer.clear();

                return messageCommand.getParserForType().parseFrom(ByteString.copyFrom(byteBuffer));
            }
        } catch (Exception e) {
            log.error("decode exception, " + RemotingHelper.parseChannelRemoteAddr(ctx.channel()), e);
            RemotingUtil.closeChannel(ctx.channel());
        } finally {
            if (null != frame) {
                frame.release();
            }
        }

        return null;
    }
}
