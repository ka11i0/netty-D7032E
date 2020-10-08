package io.netty.example.p2p;


import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.example.echo.EchoClientHandler;
import io.netty.example.echo.EchoServerHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.CharsetUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;


public class Node {

    public static void main(String[] args) throws Exception{
        Node me = new Node(1, "127.0.0.1", 8007);
        Node carl = new Node(2, "127.0.0.1", 8007);
        //Node zero = new Node(3, "127.0.0.1", 8007);

        me.addNode(carl);
        //me.addNode(zero);
        me.run();
    }

    private final int id;
    private final String ip;
    private final int port;
    private String status;
    private Node[] othernodes;
    private ServerBootstrap server;
    private Bootstrap client;


    public Node(int id, String ip, int port) {
        this.id = id;
        this.ip = ip;
        this.port = port;
        othernodes = new Node[0];
        server = new ServerBootstrap();
        client = new Bootstrap();
        status = "active";
    }

    private void addNode(Node n) {
        Node[] nodes = new Node[othernodes.length+1];
        for(int i = 0; i < othernodes.length; i++) {
            nodes[i] = othernodes[i];
        }
        nodes[othernodes.length] = n;
        othernodes = nodes;
    }

    // Connect to all the nodes in the connections collection
    private Channel[] connectToAll() throws InterruptedException {
        Channel[] channels = new Channel[othernodes.length];
        int i = 0;
        for(Node node: othernodes) {
            Channel connection = client.connect(node.ip, node.port).sync().channel();
            channels[i] = connection;
            i++;
        }
        return channels;
    }

    public void changeStatus(String s) {
        status = s;
    }

    public void run() throws InterruptedException {
        EventLoopGroup servergroup = new NioEventLoopGroup();
        EventLoopGroup clientgroup = new NioEventLoopGroup();
        try {
            // Server part
            server.group(servergroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            socketChannel.pipeline().addLast(new NodeServerHandler());
                        }
                    });

            // Client part
            client.group(clientgroup);
            client.channel(NioSocketChannel.class);
            client.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel socketChannel) throws Exception {
                    socketChannel.pipeline().addLast(new NodeClientHandler());
                }
            });

            // Start the server.
            ChannelFuture f = server.bind(port).sync();

            // Start the client and connect to all other nodes

            Channel[] connections = connectToAll();

            System.out.println("Enter msg to send to othern nodes");
            Scanner scanner = new Scanner(System.in);
            while (scanner.hasNextLine()) {
                String msg = scanner.nextLine();
                System.out.println("Input from node "+ id + ":"+msg);
                for(Channel ch: connections) {
                    ByteBuf msgBuffer = Unpooled.wrappedBuffer(msg.getBytes(CharsetUtil.UTF_8));
                    ch.writeAndFlush(msgBuffer);
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            // Shut down all event loops to terminate all threads.
            servergroup.shutdownGracefully().sync();
            clientgroup.shutdownGracefully().sync();
        }

    }
}
