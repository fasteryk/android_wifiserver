#include <jni.h>

#include <time.h>
#include <stdlib.h>
#include <stdio.h>

#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/features2d.hpp>

#include <android/log.h>
#define TAG "wifiserver"
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, TAG, __VA_ARGS__)

using namespace std;
using namespace cv;

extern "C" {


JNIEXPORT void JNICALL
Java_com_testapp_wifiserver_CameraFragment2_ImageProcess(JNIEnv *jenv, jobject, jlong srcMat, jlong dstMat)
{
    Mat &input_mat = *(Mat *) srcMat;
    Mat &output_mat = *(Mat *) dstMat;

    Mat bgr_mat(480, 720, CV_8UC3);

    int64 start_time, finish_time;

    try {
        //start_time = getTickCount();
        cvtColor(input_mat, bgr_mat, COLOR_YUV2BGR_YUYV);
        //finish_time = getTickCount();

        //float consuming = (float)(finish_time - start_time) * 1000 / (float)getTickFrequency();
        //LOGV("to bgr: %.2f", consuming);

        time_t t = time(NULL);
        struct tm tm = *localtime(&t);

        char current_time[50];
        sprintf(current_time, "%02d:%02d:%02d", tm.tm_hour, tm.tm_min, tm.tm_sec);

        cv::putText(bgr_mat, current_time, cv::Point(20, 30), cv::FONT_HERSHEY_SIMPLEX, 0.6,
                    cv::Scalar(0, 0, 255), 2);

        //start_time = getTickCount();
        cvtColor(bgr_mat, output_mat, COLOR_BGR2YUV_I420);
        //finish_time = getTickCount();

        //consuming = (float) (finish_time - start_time) * 1000 / (float)getTickFrequency();
        //LOGV("to yuv: %.2f", consuming);
    } catch (const cv::Exception &e) {
        LOGV("cv::Exception: %s", e.what());
    } catch (...) {
        LOGV("caught unknown exception");
    }
}

}