package com.fenghaha.downloader;

import java.io.File;

/**
 * Created by FengHaHa on2018/6/8 0008 2:06
 */
public interface DownloadCallback {
    void onPause();
    void onProcess(long total,long current,float speed,int percent);
    void onCancel();
    void onFinish(File file);
    void onFailed(String reason);
}
