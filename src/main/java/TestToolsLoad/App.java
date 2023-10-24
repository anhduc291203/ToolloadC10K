package Nettyserver;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
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
                            ch.pipeline().addLast(new HttpServerCodec(), new SimpleHandler());
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

                ctx.writeAndFlush(response);
                ctx.close();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}
