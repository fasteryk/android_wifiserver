package com.testapp.wifiserver;

import android.app.Activity;
import android.media.Image;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import com.mediatek.carcorder.CameraDevice;
import com.mediatek.carcorder.CarcorderManager;
import com.mediatek.carcorder.CameraDevice.YUVCallback;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;

import static com.mediatek.carcorder.CameraDevice.YUVCallbackType.yuvCBAndRecord;
import static com.mediatek.carcorder.CameraDevice.YUVFrameType.yuvPreviewFrame;
import static com.mediatek.carcorder.CameraInfo.CAMERA_MAIN_SENSOR;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

public class CameraFragment2 extends Fragment implements YUVCallback {
    private static final String TAG = "CameraFragment2";

    private final String YUV_FILE =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/frame.yuv";
    private final String H264_FILE =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/record.h264";

    private AutoFitTextureView mTextureView;
    private CameraDevice mCameraDevice;

    private File mFile;
    private FileOutputStream mOutputFile;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    private int frameCount = 0;
    private boolean saved = false;
    private Mat mYUV422Image;
    private int inputIndex = 0;

    private AvcEncoder mEncoder;
    private boolean mEncodeInitSuccess = false;

    private BlockingQueue<Mat> mFrameQueue = new ArrayBlockingQueue(10);

    private VideoEncodingThread mEncodingThread;

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            //configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };

    public static CameraFragment2 newInstance() {
        return new CameraFragment2();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        System.loadLibrary("image_process");

        mYUV422Image = new Mat(480, 720, CvType.CV_8UC2);
        mEncoder = new AvcEncoder();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        mTextureView = (AutoFitTextureView) view.findViewById(R.id.texture);
    }

    @Override
    public void onResume() {
        super.onResume();

        //startBackgroundThread();

        mFile = new File(H264_FILE);

        try {
            mOutputFile = new FileOutputStream(mFile);
        } catch (IOException e) {
            Log.d(TAG, e.getMessage());
            e.printStackTrace();
        }

        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }

        mEncodeInitSuccess = mEncoder.init(720, 480, 30, 1000000);

        mFrameQueue.clear();

        mEncodingThread = new VideoEncodingThread();
        mEncodingThread.start();
    }

    @Override
    public void onPause() {
        super.onPause();

        //stopBackgroundThread();

        mEncodingThread.interrupt();
        try {
            mEncodingThread.join();
            mEncodingThread = null;
        } catch (Exception e) {

        }

        mEncoder.close();

        closeCamera();

        try {
            if (mOutputFile != null) {
                mOutputFile.close();
                Log.d(TAG, "file saved");
            }
        } catch (IOException e) {
            Log.d(TAG, e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onYuvCbFrame(byte[] data, int cameraId, int size) {
        //Log.d(TAG, "camera id: " + cameraId + ", avaliable frame size " + size);

        mYUV422Image.put(0, 0, data);

        Mat yuv420Image = new Mat(480*3/2, 720, CvType.CV_8UC1);
        ImageProcess(mYUV422Image.getNativeObjAddr(), yuv420Image.getNativeObjAddr());

        try {
            mFrameQueue.add(yuv420Image);
        } catch (Exception e) {
            Log.d(TAG, e.getMessage());
        }
    }

    private void openCamera(int width, int height) {
        CarcorderManager carcorderManager = CarcorderManager.get();
        mCameraDevice = carcorderManager.openCameraDevice(CAMERA_MAIN_SENSOR);

        CameraDevice.Parameters param = mCameraDevice.getParameters();
        param.setCameraId(CAMERA_MAIN_SENSOR);
        param.setYUVCallbackType(yuvCBAndRecord);
        param.enableTimestampForPrv(0);
        param.setPreviewSize(720, 480);

        List<CameraDevice.Size> list = param.getSupportedPreviewSizes();

        for (CameraDevice.Size s : list) {
            Log.d(TAG, "support size: " + s.width + "x"+ s.height);
        }

        mCameraDevice.setYuvCallback(this);
        mCameraDevice.setParameters(param);
        mCameraDevice.setPreviewSurface(new Surface(mTextureView.getSurfaceTexture()));
        mCameraDevice.startPreview();
        mCameraDevice.startYuvVideoFrame(yuvPreviewFrame);
    }

    private void closeCamera() {
        mCameraDevice.setYuvCallback(null);
        mCameraDevice.startYuvVideoFrame(yuvPreviewFrame);
        mCameraDevice.stopPreview();
        mCameraDevice.release();
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static class FrameSaver implements Runnable {
        private final Mat mFrameData;
        private final File mFile;

        FrameSaver(Mat frame, File file) {
            mFrameData = frame;
            mFile = file;
        }

        @Override
        public void run() {
            FileOutputStream output = null;
            try {
                byte[] data = new byte[(int)mFrameData.total()];
                mFrameData.get(0, 0, data);
                output = new FileOutputStream(mFile);
                output.write(data);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (null != output) {
                    try {
                        output.close();
                        Log.d(TAG, "frame saved");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private class VideoEncodingThread extends Thread {
        private static final int BUFF_LENGTH = 500;
        private static final int PORT = 8888;

        private DatagramSocket udpSocket = null;
        private byte[] udpDataBuff;

        VideoEncodingThread() {
            udpDataBuff = new byte[BUFF_LENGTH];
            udpDataBuff[0] = 'D';
        }

        @Override
        public void run() {
            try {
                udpSocket = new DatagramSocket();

                while (true) {
                    Mat frameData = mFrameQueue.take();

                    if (!mEncodeInitSuccess)
                        return;

                    byte[] input = new byte[(int) frameData.total()];
                    frameData.get(0, 0, input);

                    byte[] encData = mEncoder.encode(input);

                    SocketAddress address = ((MainActivity) getActivity()).clientAddress();
                    if (address != null) {
                        InetAddress dstAddr = ((InetSocketAddress)address).getAddress();

                        byte[] head = new byte[5];
                        head[0] = 'H';
                        head[1] = (byte)((encData.length>>24)&0xff);
                        head[2] = (byte)((encData.length>>16)&0xff);
                        head[3] = (byte)((encData.length>>8)&0xff);
                        head[4] = (byte)(encData.length&0xff);
                        sendPacket(head, head.length, dstAddr, PORT);

                        if (encData.length <= (BUFF_LENGTH-1)) {
                            System.arraycopy(encData, 0, udpDataBuff, 1, encData.length);
                            sendPacket(udpDataBuff, encData.length+1, dstAddr, PORT);
                        } else {
                            int offset = 0;
                            for (int i = 0; i < encData.length/(BUFF_LENGTH-1); i++) {
                                System.arraycopy(encData, offset, udpDataBuff, 1, BUFF_LENGTH-1);
                                sendPacket(udpDataBuff, BUFF_LENGTH, dstAddr, PORT);
                                offset += BUFF_LENGTH-1;
                            }

                            int remaining = encData.length%(BUFF_LENGTH-1);
                            if ( remaining != 0) {
                                System.arraycopy(encData, offset, udpDataBuff, 1, remaining);
                                sendPacket(udpDataBuff, remaining+1, dstAddr, PORT);
                            }
                        }
                    }
                }
            } catch (SocketException e) {
                Log.d(TAG, e.getMessage());
            } catch (InterruptedException e) {
                Log.d(TAG, "encoding thread exit");
            } catch (Exception e) {
                Log.d(TAG, e.getMessage());
                e.printStackTrace();
            }
        }

        private void sendPacket(byte[] data, int length, InetAddress address, int port) {
            DatagramPacket packet = new DatagramPacket(data, length, address, port);
            try {
                udpSocket.send(packet);
                Log.d(TAG, "send packet, type: " + (char) data[0] + ", length: " + length +
                        ", dst: " + address + ", port: " + port);
            } catch (Exception e) {
                Log.d(TAG, "send packet error, " + e.getMessage());
            }
        }
    }

    public static native void ImageProcess(long srcMat, long dstMat);
}
