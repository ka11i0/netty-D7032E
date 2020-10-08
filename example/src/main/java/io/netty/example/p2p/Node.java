package io.netty.example.p2p;


import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;


public class Node {

    private final int id;
    private final String ip;
    private final int port;
    private String status;
    private final Channel[] othernodes;
    private ServerBootstrap server;
    private Bootstrap client;


    public Node(int id, String ip, int port) {
        this.id = id;
        this.ip = ip;
        this.port = port;
        server = new ServerBootstrap();
        client = new Bootstrap();
        status = "active";
    }

    // Connect to all the nodes in the connections collection
    private Channel[] connectToAll(Cha) throws InterruptedException {
        Channel[] channels = new Channel[othernodes.length];
        for(Node node: othernodes) {
            Channel connection = client.connect(node.ip, node.port).sync().channel();
        }
        return channels;
    }

    public void changeStatus(String s) {
        status = s;
    }

    public void run() {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            server.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 100)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            //p.addLast(new LoggingHandler(LogLevel.INFO));
                            p.addLast(new NodeServerHandler());
                        }
                    });

            // Client part
            Bootstrap c = new Bootstrap();
            c.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            //p.addLast(new LoggingHandler(LogLevel.INFO));
                            p.addLast(new NodeClientHandler());
                        }
                    });

            // Start the server.
            ChannelFuture f = server.bind(port).sync();

            // Start the client and connect to all other nodes

            Channel[] connections = connectToAll();

            // Read commands from the stdin.
            System.out.println("Enter 'msg' to 'id' (quit to end)");
            ChannelFuture lastWriteFuture = null;
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            for (;;) {
                String line = in.readLine();
                if (line == null || "quit".equalsIgnoreCase(line)) {
                    break;
                }

                if(id>=connections.length) {
                    System.out.print("invalid id");
                    continue;
                }
                Channel connection = connections[id];
                // Sends the received line to the server.
                lastWriteFuture = connection.writeAndFlush(line);
            }
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        } finally {
            // Shut down all event loops to terminate all threads.
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }

    }
}
