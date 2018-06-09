# FileDownloader
一个文件下载框架

## 支持
+ 多线程下载一个(多个)文件
+ 断点续传
+ 程序内实时暂停,继续,取消下载
## 用法
1. 下载
```
Downloadtask task = new DownloadTask.TaskBuilder()
                .url(url)
                .path(saveFilePath)
                .threadCount(3)//设置线程数
                .callback(new DownloadCallback() {
                    @Override
                    public void onPause() {
                        //TODO 暂停时操作
                    }

                    @Override
                    public void onProcess(long total, long current, float speed, int percent) {
                      　//TODO 实时更新下载情况  速度单位  byte/s
                    }

                    @Override
                    public void onCancel() {
                        //TODO　取消下载
                    }

                    @Override
                    public void onFinish(File file) {
                       //TODO 处理下载好的文件
                    }

                    @Override
                    public void onFailed(String reason) {
                       //TODO　下载失败
                    }
                }).build()
                  .startDownload();
```
2. 断点续传
```
 @Override
    protected void onDestroy() {
        super.onDestroy();
        if (task != null) task.save();
    }
```
## BUG
+ 断点续传存在问题,文件可能会损坏.(几个小时都没能解决,我也很绝望啊!!!)
