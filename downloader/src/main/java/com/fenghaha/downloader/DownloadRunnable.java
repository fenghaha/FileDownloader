package com.fenghaha.downloader;

import android.content.Context;
import android.content.SharedPreferences;
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
    private long downloadedLength = 0;
    private int threadId;
    private boolean isFinish;
    private boolean isPause = false;
    private boolean isRestart = false;
    private SharedPreferences sharedPreferences;
    private String threadInfo;
    private boolean isStored;
    private static final String TAG = "DownloadRunnable";

    DownloadRunnable(DownloadTask task, long begin, long end, int threadId, Context context) {
        threadInfo = "threadInfo: name=" + task.getFileName() + "&id=" + threadId;
        sharedPreferences = context.getSharedPreferences(threadInfo, Context.MODE_PRIVATE);
        isStored = sharedPreferences.contains(threadInfo);
        this.begin = sharedPreferences.getLong("begin", begin);//恢复数据
        this.task = task;
        this.end = end;
        this.threadId = threadId;
    }

    private void threadDownload(long begin, long end) {
        Log.d(TAG, "id: " + threadId + "threadDownload:   begin = "+begin+" end = "+end);
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
                Log.d(TAG, "id: " + threadId + "  begin: " + begin  + "end: " + end);
                inputStream = connection.getInputStream();
                int len;
                byte buf[] = new byte[1024];
                while ((len = inputStream.read(buf)) != -1) {
                    if (isPause) return;
                    if (begin + downloadedLength >= end) break;
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

    @Override
    public void run() {
        Log.d(TAG, "isRestart = "+isRestart+" isPause = "+isPause);
        if (isRestart&&!isPause) {
            Log.d(TAG, "id="+threadId+"run: restart  begin="+begin+" down="+downloadedLength+"end = "+end);
            threadDownload(begin + downloadedLength - 1, end);
            downloadedLength = 0;
        } else threadDownload(begin, end);
    }


    synchronized public void saveData() {
        isPause = true;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong("begin", downloadedLength + begin - 1);
        editor.putLong("end", end);
        Log.d(TAG, "saveData:  begin = " + begin + " download = " + downloadedLength + " end  = " + end);
        editor.commit();
    }

    void cleanSP() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.commit();
    }

    public long getBegin() {
        return begin;
    }

    public void setBegin(long begin) {
        this.begin = begin;
    }

    public boolean isPause() {
        return isPause;
    }

    public void pause() {
        isPause = true;
        isRestart = false;
    }

     void restart() {
        isPause = false;
        isRestart = true;
        Log.d(TAG, "id="+threadId+"restart: 了"+"isRestart = "+isRestart+" isPause = "+isPause);

    }

    public long getEnd() {
        return end;
    }

    public void setEnd(long end) {
        this.end = end;
    }

    public int getThreadId() {
        return threadId;
    }


    public long getDownloadedLength() {
        return downloadedLength;
    }

    public boolean isFinish() {
        return isFinish;
    }
}
