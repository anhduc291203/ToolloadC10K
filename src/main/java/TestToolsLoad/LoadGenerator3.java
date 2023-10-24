package Nettyserver;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;

public class App {
    public static void main(String[] args) throws Exception {
        int port = 9000;
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline().addLast(
                                    new IdleStateHandler(60, 0, 0),
                                    new HttpServerCodec(),
                                    new SimpleHandler()
                            );
                        }
                    });

            ChannelFuture f = b.bind(port).sync();
            System.out.println("Server started on port " + port);
            f.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    static class SimpleHandler extends SimpleChannelInboundHandler<HttpObject> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
            if (msg instanceof HttpRequest) {
                ByteBuf content = Unpooled.copiedBuffer("<html><body><h1>Cảm ơn</h1></body></html>", CharsetUtil.UTF_8);
                FullHttpResponse response = new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content
                );

                response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
                response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());

                if (HttpUtil.isKeepAlive((HttpRequest) msg)) {
                    response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                    response.headers().set(HttpHeaderNames.KEEP_ALIVE, "timeout=120");  // 120 giây
                }

                ctx.writeAndFlush(response);
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) evt;
                if (event.state() == IdleState.READER_IDLE) {
                    ctx.close();  // Đóng kết nối sau 1 phút không hoạt động
                }
            } else {
                super.userEventTriggered(ctx, evt);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}
