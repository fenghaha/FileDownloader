package com.fenghaha.downloader;

import android.os.Handler;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;


/**
 * Created by FengHaHa on2018/6/8 0008 13:20
 */
public class DownloadTask {
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private List<DownloadRunnable> runnableList;
    private boolean isPause;
    private boolean isCancel;
    private int threadCount;
    private String path;
    private String url;
    private String fileName;
    private long fileLength;
    private long currentLength = 0;
    // private AtomicLong currentLength = new AtomicLong(0);
    private boolean isFinished = false;
    private DownloadCallback callback;
    private DownloadManager manager = DownloadManager.getInstance();
    private static final String TAG = "DownloadTask";
    private Handler handler = new Handler();

    private DownloadTask(TaskBuilder builder) {
        threadCount = builder.threadCount;
        path = builder.path;
        url = builder.url;
        fileName = builder.fileName;
        callback = builder.callback;
    }

    synchronized void appendCurrentLength(long size) {
        currentLength += size;
    }

    private void requestFileInfo() {
        HttpURLConnection connection = null;
        try {
            URL mUrl = new URL(url);
            String name = mUrl.getPath();
            if (fileName == null)//未指定文件名时自动生成
                fileName = name.substring(name.lastIndexOf("/") + 1);
            connection = (HttpURLConnection) mUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                fileLength = connection.getContentLength();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public void startDownload() {
        manager.startOneTask(this, callback);
    }

    void prepare() {
        requestFileInfo();
        long tcount = getThreadCount() > MAXIMUM_POOL_SIZE ? MAXIMUM_POOL_SIZE : threadCount;
        long block = fileLength / tcount; //将下载文件分段，每段的长度
        long begin = 0;
        runnableList = new ArrayList<>();
        DownloadRunnable runnable;
        for (int i = 0; i < tcount; i++) {

            if (i == tcount - 1) {
                runnable = new DownloadRunnable(this, begin, fileLength - 1, i);
            } else {
                runnable = new DownloadRunnable(this, begin, begin + block - 1, i);
            }
            Log.d(TAG, "begin: " + begin + "end: " + (begin + block - 1) + "block: " + block);
            begin += block;
            runnableList.add(runnable);
        }
    }

    //实时更新下载状态
    void refresh() {
        boolean isFinish = false;
        long lastDownloadSize = 0;
        float speed;
        Log.d(TAG, "path: " + path + "name: " + fileName);
        File file = new File(path, fileName);
        long localLength = 0;
        if (file.exists()) {
            localLength = file.length();
        }
        if (localLength == fileLength) {
            handler.post(() -> callback.onFinish());
            return;
        } else if (localLength > fileLength) {
            file.delete();
        }

        long time = System.currentTimeMillis();
        while (!isFinish) {
            if (isPause || isCancel) break;
            int percent = currentLength == fileLength ? 100 : (int) (100 * currentLength / fileLength);
            long spendTime = (System.currentTimeMillis() - time);
            if (spendTime == 0) spendTime = 1;
            Log.d(TAG, "current = " + currentLength / 1024 + "  last: " + lastDownloadSize / 1024 + " spend: " + spendTime + "ms");
            speed = ((currentLength - lastDownloadSize) / spendTime * 1000);// 单位byte/s
            callback.onProcess(fileLength, currentLength, speed, percent);
            if (percent == 100) break;
            lastDownloadSize = currentLength;
            time = System.currentTimeMillis();
            isFinish = checkFinish();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        finish(callback);
    }

    private void pause() {
        isPause = true;
        if (callback != null)
            handler.post(() -> callback.onPause());
    }

    private void cancel() {
        isCancel = true;
        if (callback != null) {
            handler.post(() -> callback.onCancel());
        }
    }

    private void finish(DownloadCallback callback) {
        handler.post(() -> {
            callback.onProcess(fileLength, fileLength, 0, 100);
            callback.onFinish();
        });

    }

    private boolean checkFinish() {
        if (runnableList != null && runnableList.size() > 0) {
            for (DownloadRunnable downloadThread : runnableList) {
                if (!downloadThread.isFinish()) {
                    return false;
                }
            }

            return true;
        }
        return false;
    }

    public List<DownloadRunnable> getRunnableList() {
        return runnableList;
    }

    public boolean isFinished() {
        return isFinished;
    }

    public long getThreadCount() {
        return threadCount;
    }

    public long getFileLength() {
        return fileLength;
    }


    public String getPath() {
        return path;
    }

    public String getUrl() {
        return url;
    }

    public String getFileName() {
        return fileName;
    }


    public static class TaskBuilder {
        private int threadCount = 3;//默认三个线程
        private String path;
        private String url;
        private String fileName;
        private DownloadCallback callback;

        private boolean check() {
            return !(path == null || url == null || callback == null);
        }

        public TaskBuilder() {
        }

        public TaskBuilder threadCount(int val) {
            threadCount = val;
            return this;
        }

        public TaskBuilder path(String val) {
            path = val;
            return this;
        }

        public TaskBuilder url(String val) {
            url = val;
            return this;
        }

        public TaskBuilder fileName(String val) {
            fileName = val;
            return this;
        }

        public TaskBuilder callback(DownloadCallback val) {
            callback = val;
            return this;
        }

        public DownloadTask build() {
            if (!check()) throw new ParamNotCompleteException("参数不完整!");
            return new DownloadTask(this);
        }
    }

    static class ParamNotCompleteException extends RuntimeException {
        public ParamNotCompleteException(String message) {
            super(message);
        }
    }
}