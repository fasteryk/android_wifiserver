package com.testapp.wifiserver;

import android.os.Message;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Calendar;
import java.util.Date;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "wifiserver";

    private NsdHelper mNsdHelper;
    private TcpServer mTcpServer;

    private SocketAddress mClientAddress = null;

    private final Object mAddressLock = new Object();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();

        if (null == savedInstanceState) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, CameraFragment2.newInstance())
                    .commit();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "Starting.");
        mNsdHelper = new NsdHelper(this);
        mNsdHelper.initializeNsd();
        mNsdHelper.registerService(6677);

        mTcpServer = new TcpServer();

        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!OpenCVLoader.initDebug())
            Log.d(TAG, "Internal OpenCV library not found");
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "Being stopped.");
        mNsdHelper.tearDown();
        mNsdHelper = null;

        mTcpServer.tearDown();

        super.onStop();
    }

    public SocketAddress clientAddress() {
        SocketAddress addr;

        synchronized (mAddressLock) {
            addr = mClientAddress;
        }

        return addr;
    }

    private class TcpServer {
        private ServerSocket mServerSocket = null;
        private Thread mThread = null;

        public TcpServer() {
            mThread = new Thread(new ServerThread());
            mThread.start();
        }

        public void tearDown() {
            mThread.interrupt();
            try {
                mServerSocket.close();
            } catch (IOException ioe) {
                Log.e(TAG, "Error when closing server socket.");
            }
        }

        class ServerThread implements Runnable {
            @Override
            public void run() {
                try {
                    mServerSocket = new ServerSocket(6677);

                    while (!Thread.currentThread().isInterrupted()) {
                        Log.d(TAG, "TCP server awaiting connection");
                        Socket socket = mServerSocket.accept();

                        SocketAddress clientAddr = socket.getRemoteSocketAddress();
                        Log.d(TAG, "connected. remote address: " + clientAddr);

                        synchronized (mAddressLock) {
                            mClientAddress = clientAddr;
                        }

                        DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
                        BufferedReader inputStream =
                                new BufferedReader(new InputStreamReader(socket.getInputStream()));

                        try {
                            while (true) {
                                String inputStr = inputStream.readLine();
                                if (inputStr != null) {
                                    Log.d(TAG, "read from the stream: " + inputStr);
                                    String outputStr = "echo from server: " + inputStr + "\n";
                                    outputStream.write(outputStr.getBytes());
                                } else {
                                    Log.d(TAG, "input string is null");
                                    break;
                                }
                            }
                        } catch (IOException e){
                            Log.e(TAG, "client processing loop exception: ", e);
                            e.printStackTrace();
                        } finally {
                            try {
                                synchronized (mClientAddress) {
                                    mClientAddress = null;
                                }
                                socket.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "server thread exception: ", e);
                    e.printStackTrace();
                } finally {
                    Log.d(TAG, "server thread exited");
                }
            }
        }
    }
}
