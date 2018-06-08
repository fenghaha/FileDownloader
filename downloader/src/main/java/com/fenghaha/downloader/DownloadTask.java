package com.fenghaha.downloader;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
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
    private ArrayList<DownloadRunnable> runnableList;
    private Context context;
    private SharedPreferences sharedPreferences;
    private boolean isPause = false;
    private boolean isCancel;
    private int threadCount;
    private String path;
    private String url;
    private String fileName;
    private long fileLength;
    private long currentLength = 0;
    private boolean isFinished = false;
    private File mFile;
    private DownloadCallback callback;
    private DownloadManager manager = DownloadManager.getInstance();
    private static final String TAG = "DownloadTask";
    private Handler handler = new Handler();
    private boolean isStored;

    private DownloadTask(TaskBuilder builder) {

        String taskInfo = "taskInfo: " + "fileName=" + builder.fileName + "&url=" + MD5Util.encrypt(builder.url) + "&path=" + MD5Util.encrypt(builder.path) + "&threadCount=" + builder.threadCount;
        sharedPreferences = builder.context.getSharedPreferences(taskInfo, Context.MODE_PRIVATE);
        isStored = sharedPreferences.contains(taskInfo);
        currentLength = sharedPreferences.getLong("currentLength", 0);
        threadCount = sharedPreferences.getInt("threadCount", builder.threadCount);
        path = sharedPreferences.getString("path", builder.path);
        url = sharedPreferences.getString("url", builder.url);
        fileName = sharedPreferences.getString("fileName", builder.fileName);
        callback = builder.callback;
        context = builder.context;


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
                mFile = new File(path, fileName);
                RandomAccessFile raf = new RandomAccessFile(mFile, "rwd");
                fileLength = connection.getContentLength();
                raf.setLength(fileLength);

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
        File file = new File(path, fileName);
        if (file.exists()) {
            file.delete();
        }
        long tcount = getThreadCount() > MAXIMUM_POOL_SIZE ? MAXIMUM_POOL_SIZE : threadCount;
        long block = fileLength / tcount; //将下载文件分段，每段的长度
        long begin = 0;
        runnableList = new ArrayList<>();
        DownloadRunnable runnable;
        for (int i = 0; i < tcount; i++) {

            if (i == tcount - 1) {
                runnable = new DownloadRunnable(this, begin, fileLength *2, i, context);
            } else {
                runnable = new DownloadRunnable(this, begin, begin + block - 1, i, context);
            }
            Log.d(TAG, "begin: " + begin + "end: " + (begin + block - 1) + "block: " + block);
            begin += block;
            runnableList.add(runnable);
        }
    }

    private void saveThreadAtPause() {
        for (DownloadRunnable r :
                runnableList) {
            r.setBegin(r.getBegin() + r.getDownloadedLength());
        }
    }

    //实时更新下载状态
    void refresh() {
        long lastDownloadSize = 0;
        float speed;
        Log.d(TAG, "path: " + path + "name: " + fileName);
        long time = System.currentTimeMillis();
        while (!isFinished) {
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
            isFinished = checkFinish();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (!isPause)
            finish(callback);
    }

    public void save() {
        isPause = true;
        //整个下载项目保存信息
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("threadCount", threadCount);
        editor.putString("path", path);
        editor.putString("url", url);
        editor.putString("fileName", fileName);
        editor.putLong("currentLength", currentLength);
        editor.commit();
        //各个线程保存信息
        for (DownloadRunnable r :
                runnableList) {
            r.saveData();
        }
    }

    public void pause() {
        isPause = true;
        saveThreadAtPause();
        for (DownloadRunnable r :
                runnableList) {
            r.pause();
        }
        if (callback != null)
            handler.post(() -> callback.onPause());
    }

    public void restart() {
        for (DownloadRunnable r :
                runnableList) {
            r.restart();
        }
        startDownload();
        isPause = false;
    }

    public void cancel() {
        isCancel = true;
        mFile.delete();
        if (callback != null) {
            handler.post(() -> callback.onCancel());
        }
    }

    private void finish(DownloadCallback callback) {
        handler.post(() -> {
            callback.onProcess(fileLength, fileLength, 0, 100);
            callback.onFinish(mFile);
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

    public ArrayList<DownloadRunnable> getRunnableList() {
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

    public boolean isPause() {
        return isPause;
    }

    public void setPause(boolean pause) {
        isPause = pause;
    }

    public boolean isCancel() {
        return isCancel;
    }

    public void setCancel(boolean cancel) {
        isCancel = cancel;
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
        private Context context;

        private boolean check() {//检查必要属性
            return !(path == null || url == null || callback == null || context == null);
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

        public TaskBuilder context(Context val) {
            context = val;
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