package com.fenghaha.downloader;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * Created by FengHaHa on2018/6/5 0005 21:48
 */
public class DownloadRunnable implements Runnable {
    private DownloadTask task;
    private long begin;
    private long end;
    private int downloadedLength = 0;
    private int threadId;
    private boolean isFinish;
    private static final String TAG = "DownloadRunnable";

    public DownloadRunnable(DownloadTask task, long begin, long end, int threadId) {
        this.task = task;
        this.begin = begin;
        this.end = end;
        this.threadId = threadId;
    }

    @Override
    public void run() {
        if (begin >= end) {
            isFinish = true;
            return;
        }
        isFinish = false;
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        RandomAccessFile raf = null;

        try {
            URL url = new URL(task.getUrl());
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setRequestMethod("GET");
            //指定下载位置 bytes=begin-end
            connection.setRequestProperty("Range", "bytes=" + begin + "-" + end);

            Log.d(TAG, "ResponseCode: " + connection.getResponseCode());
            if (connection.getResponseCode() == 206) {
                raf = new RandomAccessFile(new File(task.getPath(), task.getFileName()), "rwd");
                raf.seek(begin);
                Log.d(TAG, "begin: " + begin + "end: " + end);
                inputStream = connection.getInputStream();
                int len;
                byte buf[] = new byte[1024];
                while ((len = inputStream.read(buf)) != -1) {
                    raf.write(buf, 0, len);
                    downloadedLength += len;
                    task.appendCurrentLength(len);
                }
                isFinish = true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (connection != null)
                connection.disconnect();
            try {
                if (raf != null)
                    raf.close();
                if (inputStream != null)
                    inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    public int getDownloadedLength() {
        return downloadedLength;
    }

    public boolean isFinish() {
        return isFinish;
    }
}
