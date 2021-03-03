package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;

import com.example.myapplication.ipcam.CameraModule;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 1234;
    private CameraModule cameraModule;

    private TextureView mTextureView;







    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //  this.webServer=new WebServer(8080);
        //  this.webServer.OpenServer();
        initTextureView();
        cameraModule=new CameraModule(this,mTextureView);

        //sd카드 승인?
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MODE_PRIVATE);
    }

    @Override
    protected void onDestroy() {


        super.onDestroy();
        cameraModule.close();
        //테스트
    }

    private void initTextureView() {
        mTextureView = (TextureView) findViewById(R.id.textureView);
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                Log.e("cklee", "MMM onSurfaceTextureAvailable");
                cameraModule.openCamera();
                //  openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
                Log.e("cklee", "MMM onSurfaceTextureSizeChanged");
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                Log.e("cklee", "MMM onSurfaceTextureDestroyed");
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
                // 화면 갱신시마다 불림
//                Log.e("cklee", "MMM onSurfaceTextureUpdated");
            }
        });
    }








    public void test(View v)
    {
        cameraModule.closeCameraPreviewSession();
    }
    public void test2(View v)
    {
        cameraModule.takePicture();

    }
    public void test3(View v){


        cameraModule.startRecordingVideo();
    }

    public void test4(View v)
    {
        cameraModule.stopRecordingVideo();
    }

    public void test5(View v) {
        cameraModule.takePictureStremming();
    }

}