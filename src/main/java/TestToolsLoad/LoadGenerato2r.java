package TestToolsLoad;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.Charset;



import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;




public class LoadGenerator {
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failCount = new AtomicInteger(0);
    private final AtomicInteger totalRequestCount = new AtomicInteger(0);
    private final AtomicInteger responseCounter = new AtomicInteger(0);




    public LoadGenerator() {
    }




    public void generateLoad() throws InterruptedException {
        int threads = Integer.parseInt(System.getProperty("threads"));
        int connectionsPerThread = Integer.parseInt(System.getProperty("connectionsPerThread"));
        int reqPerSec = Integer.parseInt(System.getProperty("reqPerSec"));
        int port = Integer.parseInt(System.getProperty("app.port"));
	    String host = System.getProperty("app.host");

        CountDownLatch latch = new CountDownLatch(threads * connectionsPerThread * reqPerSec);
        CountDownLatch connectionLatch = new CountDownLatch(threads * connectionsPerThread);

        NioEventLoopGroup group = new NioEventLoopGroup(threads);

        List<Channel> allChannels = Collections.synchronizedList(new ArrayList<>());

        ExecutorService threadPool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            threadPool.submit(() -> {
                for (int j = 0; j < connectionsPerThread; j++) {
                    try {
                        Bootstrap bootstrap = createBootstrap(group, latch);
                        Channel channel = bootstrap.connect(host, port).sync().channel();
                        allChannels.add(channel);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        connectionLatch.countDown();
                    }
                }
            });
        }


        Thread.sleep(10000);
        for (int i = 0; i < reqPerSec; i++) {
            CountDownLatch responseLatch = new CountDownLatch(allChannels.size());
            for (int j = 0; j < allChannels.size(); j ++) 
	    {
		                    sendRequest(allChannels.get(j), responseLatch, "abcefg"+j+j*i+i, String.valueOf(123));
	    }
            responseLatch.await();
            Thread.sleep(1000);
        }


        latch.await();
        connectionLatch.await();


        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException ie) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        closeAllChannels(allChannels);
        group.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        summarizeResults();
    }




    private Bootstrap createBootstrap(NioEventLoopGroup group, CountDownLatch latch) {
        return new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(new IdleStateHandler(120, 120, 240), new HttpClientCodec(), new HttpObjectAggregator(1000000), new SimpleChannelInboundHandler<FullHttpResponse>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) {
                                String connectionHeader = msg.headers().get(HttpHeaderNames.CONNECTION);
                                int currentCount = responseCounter.incrementAndGet();
                                System.out.println("Connection header: " + connectionHeader + msg.status() + " | Occurrence: " + currentCount);
                                if (msg.status().equals(HttpResponseStatus.OK)) {
                                    successCount.incrementAndGet();
                                } else {
                                    failCount.incrementAndGet();
                                }latch.countDown();
                            }
                        }, new ChannelInboundHandlerAdapter() {
                            @Override
                            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                if (evt instanceof IdleStateEvent) {
                                    IdleStateEvent event = (IdleStateEvent) evt;
                                    if (event.state() == IdleState.READER_IDLE) {
                                        System.out.println("Không có hoạt động đọc trong khoảng thời gian cụ thể, có thể kết nối bị timeout!");
					latch.countDown();

                                        ctx.close();
                                    } else if (event.state() == IdleState.WRITER_IDLE) {
                                        System.out.println("Không có hoạt động ghi trong khoảng thời gian cụ thể");
					latch.countDown();
					failCount.incrementAndGet();
					ctx.close();
                                    } else if (event.state() == IdleState.ALL_IDLE) {
					    failCount.incrementAndGet();
                                        System.out.println("Không có hoạt động đọc và ghi trong khoảng thời gian cụ thể");
					latch.countDown();
			//		System.out.println(latch);
					ctx.close();
                                    }
                                }
                                super.userEventTriggered(ctx, evt);
                            }
                        });
                    }
                });
    }




    private void sendRequest(Channel channel, CountDownLatch responseLatch, String usernameValue, String passwordValue) {
        totalRequestCount.incrementAndGet();
//        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
//        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
//        request.headers().set(HttpHeaderNames.HOST, "localhost");
//        request.headers().set(HttpHeaderNames.USER_AGENT, "JavaClient/1.0");
//        request.headers().set(HttpHeaderNames.ACCEPT, "*/*");

	 String postData = "username=" + usernameValue + "&password=" + passwordValue;
    	 ByteBuf content = Unpooled.copiedBuffer(postData, Charset.defaultCharset());

//	String jsonData = "{\"username\":\"" + usernameValue + "\", \"password\":\"" + passwordValue + "\"}";
//	ByteBuf content = Unpooled.copiedBuffer(jsonData, Charset.forName("UTF-8"));

	// System.out.println(postData);
         HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/register", content);

	 request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
 	 request.headers().set(HttpHeaderNames.HOST, "localhost");
	 request.headers().set(HttpHeaderNames.USER_AGENT, "JavaClient/1.0");
	 request.headers().set(HttpHeaderNames.ACCEPT, "*/*");
	 request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/x-www-form-urlencoded");
	 request.headers().set(HttpHeaderNames.CONTENT_LENGTH, postData.length());

 	 if (channel.isActive()) {
            channel.writeAndFlush(request).addListener(future -> {
                if (future.isSuccess()) {
//                    successCount.incrementAndGet();
                } else {
                    failCount.incrementAndGet();
                    System.err.println("Error sending request: " + future.cause().getMessage());
                }
                responseLatch.countDown();  // Count down for each response or failure
            });
        } else {
            failCount.incrementAndGet();
            System.out.println("Channel không còn hoạt động");
            responseLatch.countDown();
        }


    }
    private void closeAllChannels(List<Channel> allChannels) {
        for (int j = 0; j < allChannels.size(); j ++) {
		            if (allChannels.get(j).isActive()) {
				                    allChannels.get(j).close().syncUninterruptibly();
						                }
			            }
    }


    private void summarizeResults() {
        try {
            String summary = "Total Requests: " + totalRequestCount.get() +
                    " | Successful Requests: " + successCount.get() +
                    " | Failed Requests: " + failCount.get() + "| Percentage Successful Requests:" +  (successCount.get() * 1.0)/ (totalRequestCount.get() * 1.0);
            System.out.println(summary);


	         Files.write(Paths.get("results2.txt"), summary.getBytes(), StandardOpenOption.CREATE);
  	 } catch (IOException e) {
           System.out.println("Failed to write to file: results.txt");
            e.printStackTrace();
        }
    }




    public static void main(String[] args) throws InterruptedException {
        LoadGenerator generator = new LoadGenerator();
        generator.generateLoad();
    }
}

