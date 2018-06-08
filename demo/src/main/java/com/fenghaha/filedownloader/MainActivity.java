package com.fenghaha.filedownloader;


import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.fenghaha.downloader.DownloadCallback;
import com.fenghaha.downloader.DownloadTask;

public class MainActivity extends AppCompatActivity {
    private String saveFilePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
        bindViews();
    }

    private void bindViews() {
        Button button = findViewById(R.id.bt_download);
        EditText editText = findViewById(R.id.et_url);
        button.setOnClickListener(v -> download(editText.getText().toString()));

    }

    private void download(String url) {
        DownloadTask task = new DownloadTask.TaskBuilder()
                .url(url)
                .path(saveFilePath)
                .threadCount(3)
                .callback(new DownloadCallback() {
                    @Override
                    public void onPause() {
                        Toast.makeText(MainActivity.this, "下载被暂停掉了!", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onProcess(long total, long current, float speed, int percent) {
                        Log.d(TAG, "总共: " + total/1024+"kb  " + "下载了: " + current/1024+"kb  " + "速度: " + speed/1024+"kb/s  "  + "百分比: " + percent+"%");
                    }

                    @Override
                    public void onCancel() {
                        Toast.makeText(MainActivity.this, "下载被取消了!", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFinish() {
                        Toast.makeText(MainActivity.this, "下完了!", Toast.LENGTH_SHORT).show();

                    }

                    @Override
                    public void onFailed(String reason) {
                        Toast.makeText(MainActivity.this, "下载失败了: " + reason, Toast.LENGTH_SHORT).show();
                    }
                }).build();
        task.startDownload();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 1:
                if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "权限被拒绝了-_-", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
        }
    }

}
