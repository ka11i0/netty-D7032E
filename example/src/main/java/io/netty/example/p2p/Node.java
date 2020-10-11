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
        Node me = new Node("me", "127.0.0.1", 8007);
        Node otoo = new Node("otoo", "127.0.0.1", 8008);

        //me.addNode(otoo);
        //otoo.addNode(me);
        otoo.startServer();

        me.run();

        //otoo.startClient();
    }

    private final String id;
    private final String ip;
    private final int port;
    private String status;
    private Node[] othernodes;
    private ServerBootstrap server;
    private Bootstrap client;


    public Node(String id, String ip, int port) {
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
    public void run() throws InterruptedException {
        startServer();
        startClient();
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
    private Channel connect(String alias) throws InterruptedException {
        int i = 0;
        for(Node node: othernodes) {
            if (node.id.equals(alias)) {
                break;
            }
            i++;
        }
        return client.connect(othernodes[i].ip, othernodes[i].port).sync().channel();
    }

    public void changeStatus(String s) {
        status = s;
    }

    public void startServer() throws InterruptedException {
        EventLoopGroup servergroup = new NioEventLoopGroup();
        try {
            server.group(servergroup)
                  .channel(NioServerSocketChannel.class)
                  .childHandler(new ChannelInitializer<SocketChannel>() {
                      @Override
                      protected void initChannel(SocketChannel socketChannel) throws Exception {
                          socketChannel.pipeline().addLast(new NodeServerHandler());
                      }
                  });

            // Start the server.
            ChannelFuture f = server.bind(port).sync();
            System.out.println("Node "+id+" started");
        } finally {
           // servergroup.shutdownGracefully().sync();
        }
    }

    public void handleInput(String[] input) throws InterruptedException {
        //input syntax: send "msg" to "index"
        if (input[0].equals("send")) {
            Channel ch = connect(input[3]);
            ByteBuf msgBuffer = Unpooled.wrappedBuffer(input[1].getBytes(CharsetUtil.UTF_8));
            ch.writeAndFlush(msgBuffer);
            ch.closeFuture();
        }
        //input add_node: add_node "id" "ip" "port"
        else if (input[0].equals("add_node")) {
            Node n = new Node(input[1], input[2], Integer.parseInt(input[3]));
            addNode(n);
        }
        else if (input[0].equals("help")) {
            System.out.println("To add another node use: add_node \"id\" \"ip\" \"port\"");
            System.out.println("To send a message: send \"msg\" to \"index\"");
        }
        else {
            System.out.println("Not a command, try help for commands and syntax");
        }
    }

    public void startClient() throws InterruptedException {
        EventLoopGroup clientgroup = new NioEventLoopGroup();
        try {
            client.group(clientgroup);
            client.channel(NioSocketChannel.class);
            client.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel socketChannel) throws Exception {
                    socketChannel.pipeline().addLast(new NodeClientHandler());
                }
            });
            Scanner scanner = new Scanner(System.in);
            while (scanner.hasNextLine()) {
                String msg = scanner.nextLine();
                String[] commands = msg.split("\\s+");
                handleInput(commands);
            }
        } finally {
            // Shut down all event loops to terminate all threads.
            clientgroup.shutdownGracefully().sync();
        }

    }
}