package com.fenghaha.downloader;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by FengHaHa on2018/6/6 0006 21:09
 */
public class DownloadManager {
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = CPU_COUNT + 1;
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final long KEEP_ALIVE = 10L;
    private List<DownloadTask> mTaskList = new ArrayList<>();
    private static final String TAG = "DownloadManager";
    private static volatile DownloadManager instance = null;
    private ExecutorService threadPool = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAXIMUM_POOL_SIZE,
            KEEP_ALIVE,
            TimeUnit.MILLISECONDS,
            new MyLinkedBlockingDeque<>()
    );

    private DownloadManager() {
    }

    public static DownloadManager getInstance() {
        if (instance == null) {//避免不必要的同步
            synchronized (DownloadManager.class) {
                if (instance == null) {
                    instance = new DownloadManager();
                }
            }
        }
        return instance;
    }

    void startTaskList(List<DownloadTask> tasks, List<DownloadCallback> callbacks) {
        for (int i = 0; i < tasks.size(); i++) {
            startOneTask(tasks.get(i), callbacks.get(i));
        }
    }


    void startOneTask( DownloadTask task,  DownloadCallback callback) {
        threadPool.execute(() -> {
            task.prepare();
            for (DownloadRunnable runnable :
                    task.getRunnableList()) {
                threadPool.execute(runnable);
            }
            task.refresh();
        });
    }


    private static class MyLinkedBlockingDeque<T> extends LinkedBlockingDeque<T> {
        @Override
        public T take() throws InterruptedException {
            return takeLast();
        }

        @Override
        public T poll() {
            return pollLast();
        }
    }
}
