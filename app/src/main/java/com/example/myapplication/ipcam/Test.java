package com.example.myapplication.ipcam;

import android.os.Environment;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class Test {

    boolean state = false;
    Queue<byte[]> queue = new LinkedList<byte[]>();
    final int QueueBuffer = 15;
    byte[] lastFrame = null;



    public void setJdata(byte[] jdata) {
        synchronized (queue) {
            if (queue.size() == QueueBuffer) {
                queue.poll();
            }
            queue.add(jdata);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    public byte[] getJdata() {
        synchronized (queue) {
            if (queue.size() > 0) {
                lastFrame = queue.poll();
            }
        }
        return lastFrame;

    }

    ;


    public void serverOn() {
        if (!th.isAlive()) {
            state = true;
            th.start();
        }
    }

    public void serverStop() {
        queue.clear();
        if (th.isAlive()) {
            state = false;

        }
    }


    Thread th = new Thread() {
        private String filePath = Environment.getExternalStorageDirectory().toString();
        private String fileFolder = "/MyCCTV/";
        private List<IPCamClient> ipCamClients;
        public ServerSocket listener;

        @Override
        public void run() {
            try {
                listener = new ServerSocket(8080);
                ipCamClients=new ArrayList<IPCamClient>();
                System.out.println("Http Server started at 8080 port");

                Thread streammingThread = new Thread(new StreammingThread(listener,ipCamClients));
                streammingThread.start();
                while (state) {
                    Socket socket = listener.accept();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    IPCamClient ipCamClient=new IPCamClient(socket);
                    ipCamClients.add(ipCamClient);



                }

            } catch (IOException e) {

            } finally {
                if(listener!=null)
                {
                    try {
                        listener.close();
                    } catch (IOException exception) {
                        exception.printStackTrace();
                    }
                }
                state=false;

            }
        };




    };

    class IPCamClient{
        Socket socket;
        DataOutputStream dos;
        String boundary = "test";
        boolean ready=false;
        public IPCamClient(Socket socket){
            this.socket=socket;
            try {
                this.dos=new DataOutputStream(socket.getOutputStream());
                //dos.writeBytes("HTTP/1.1 200 OK \r\n");


                //mime type 설정

                String stream = ("HTTP/1.0 200 OK\r\n" +
                        "Server: iRecon\r\n" +
                        "Connection: close\r\n" +
                        "Max-Age: 0\r\n" +
                        "Expires: 0\r\n" +
                        "Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n" +
                        "Pragma: no-cache\r\n" +
                        "Content-Type: multipart/x-mixed-replace; " +
                        "boundary=" + boundary + "\r\n" +
                        "\r\n");
                String stream2 = ("HTTP/1.0 200 OK\r\n" +
                        "Server: iRecon\r\n" +
                        "Keep-Alive: timeout=5, max=100\n" +
                        "Connection: Keep-Alive\r\n" +
                        "Max-Age: 0\r\n" +
                        "Expires: 0\r\n" +
                        "Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n" +
                        "Pragma: no-cache\r\n" +
                        "Content-Type: multipart/x-mixed-replace; " +
                        "boundary=" + boundary + "\r\n" +
                        "\r\n");

                dos.writeBytes(stream2);
                dos.flush();
                ready=true;
            } catch (IOException exception) {
                close();
            }
        }

        public void insertImageData(byte[] data)
        {
            try {

                String mjpeg = (
                        "--" + boundary + "\r\n" +
                                "Content-type: image/jpeg\r\n" +
                                "Content-Length: " + data.length + "\r\n" +
                                //"X-Timestamp:" + new TimeStamp() + "\r\n" +
                                "\r\n");

                dos.writeBytes(mjpeg);
                dos.write(data);
                //                              byte[] buf = new byte[1024];
                //                                long totalReadBytes = 0;
                //                                  int readBytes;
                //                                    while ((readBytes = dis.read(buf)) > 0) { //길이 정해주고 딱 맞게 서버로 보냅니다.
                //                                          dos.write(buf, 0, readBytes);
                //
                //                                           totalReadBytes += readBytes;
                //                                        }
                dos.flush();
            }catch(Exception e)
            {
                close();
            }
        }
        public void close(){
            ready=false;
            if(socket!=null)
            {
                try {
                    socket.close();
                } catch (IOException exception) {
                    exception.printStackTrace();
                }
            }
            if(dos!=null)
            {
                try {
                    dos.close();
                } catch (IOException exception) {
                    exception.printStackTrace();
                }
            }
        }

    }



    class StreammingThread implements Runnable {
        private List<IPCamClient> ipCamClients;
        ServerSocket serverSocket;

        public StreammingThread(ServerSocket serverSocket,List<IPCamClient> ipCamClients) {
            this.ipCamClients=ipCamClients;
            this.serverSocket = serverSocket;
        }

        @Override
        public void run() {
            while(state)
            {
                if(!queue.isEmpty())
                {

                    if(!ipCamClients.isEmpty())
                    {
                        byte[] data = getJdata();
                        int len=ipCamClients.size();
                        int x=-1;
                        for(int i=0;i<len;i++)
                        {
                            IPCamClient ipCamClient=ipCamClients.get(i);
                            if(ipCamClient.ready&&!ipCamClient.socket.isClosed())
                            {
                                ipCamClient.insertImageData(data);
                            }else
                            {
                                x=i;
                            }
                        }
                        if(x!=-1)
                        {
                            ipCamClients.remove(x);
                            x=-1;
                        }


                    }
                }
            }
        }
    }



}
