package com.chenzhu.screencapture.mypush;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Point;
import android.os.Build;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.chenzhu.screencapture.core.RtmpManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static android.os.SystemClock.sleep;

public class MainActivity extends AppCompatActivity implements RtmpManager.Callback{

    String url ="rtmp://192.168.30.13/live/0";
    private String TAG ="cz-MainActivity";
    int width;
    int height;
    ExecutorService cachedThreadPool = Executors.newCachedThreadPool();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        RtmpManager.getInstance().setCallback(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10);
        }

        Toast.makeText(this,url,Toast.LENGTH_LONG).show();

//        cachedThreadPool.execute(new Runnable() {
//            @Override
//            public void run() {
//                sleep(500);
//                sendKey(560,680);
//                sleep(500);
//                sendKey(460,680);
//            }
//        });

       // RtmpManager.getInstance().startScreenCapture(this, url);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        RtmpManager.getInstance().onActivityResult(requestCode, resultCode, data);
    }

    public void stoplive(View view) {
        RtmpManager.getInstance().stopScreenCapture();
    }

    public void startlive(View view) {
        RtmpManager.getInstance().startScreenCapture(this, url);
    }

    @Override
    public void onStatus(int status) {
        if (status == RtmpManager.STATUS_START) {

        } else {

        }
    }



    protected void getPix() {
        Display display = getWindowManager().getDefaultDisplay();
        Point outSize = new Point();
        display.getSize(outSize);
        width = outSize.x;
        height = outSize.y;
        Log.i("Lgq", "手机像素为：" + width + "x" + height);
    }

    public void sendKey(int x ,int y){
        final Instrumentation inst = new Instrumentation();
        final long dowTime = SystemClock.uptimeMillis();
        inst.sendPointerSync(MotionEvent.obtain(dowTime, dowTime, MotionEvent.ACTION_DOWN, x, y, 0));
        inst.sendPointerSync(MotionEvent.obtain(dowTime, dowTime, MotionEvent.ACTION_UP, x, y, 0));
    }



}
