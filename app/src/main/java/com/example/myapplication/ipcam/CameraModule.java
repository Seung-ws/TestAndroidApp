package com.example.myapplication.ipcam;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.example.myapplication.MainActivity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;

public class CameraModule {

    private static final int REQUEST_CAMERA_PERMISSION = 1234;

    //카메라 엑티비티
    private MainActivity context;

    //카메라 관리자
    private CameraManager cameraManager;


    //take picture 에 주로 쓰이는 객체
    private  CameraDevice cameraDevice;



    // 이미지 출력을 위한 기능별 세션
    //프리뷰용
    private CameraCaptureSession cameraPreviewSession;
    //캡처용
    private CameraCaptureSession cameraCaptureSession;
    //비디오용
    private CameraCaptureSession cameraVideoSession;



    //카메라 리스트  넘버
    private String oneCameraId;
    //캡쳐 해상도 리스트 넘버
    private Size imageDimension;
    //레코딩 해상도 리스트 넘버
    private Size videoDimension;

    //저장 경로 데이터
    private String filePath;
    private String fileFolder;



    //이미지 촬영
    private ImageReader imageReader;
    //비디오 촬영
    private MediaRecorder mediaRecorder;
    private boolean isVideoRecording=false;
    //이미지 저장 버퍼
    private LinkedList<Bitmap> mQueue = new LinkedList<Bitmap>();
    private static final int MAX_BUFFER = 15;


    //텍스처 그리기 공간
    private TextureView textureView;

    //자동저장 날짜
    private SimpleDateFormat fileName=new SimpleDateFormat("yyyyMMddHHmmssSSS");
    private SimpleDateFormat dateFileFolder=new SimpleDateFormat("yyyyMMdd");

    //기울기센서
    private float mDegrees;
    private SensorManager mSensorManager;
    private Sensor mAccSensor;
    private SensorEventListener sensorEventListener;
    Test test=new Test();

    public CameraModule(MainActivity context, TextureView textureView)
    {
        this.context=context;
        this.cameraManager=(CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        this.textureView=textureView;
        filePath=Environment.getExternalStorageState().toLowerCase();
        //카메라셋팅
        initCamera(0,21);
        //센서 온
        initSensor();
        //카메라 온



    }

    //초기 설정 갱신
    private boolean initCamera(int cameraIndex, int sizeIndex){
        try{
            //카메라 리스트를 호출 저장
            String[] cameraIdArray = cameraManager.getCameraIdList();
            Log.e("cklee", "MMM cameraIds = " + Arrays.deepToString(cameraIdArray));
            //초기 카메라 설정은 0번 camera를 사용
            oneCameraId = cameraIdArray[cameraIndex];
            CameraCharacteristics cameraCharacter = cameraManager.getCameraCharacteristics(oneCameraId);
            Log.e("cklee", "MMM camraCharacter = " + cameraCharacter);

            StreamConfigurationMap map = cameraCharacter.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizesForStream = map.getOutputSizes(SurfaceTexture.class);
            Log.e("cklee", "MMM sizesForStream = " + Arrays.deepToString(sizesForStream));

            // 가장 큰 사이즈부터 들어있다
            // 캡쳐 모드와 레코딩 모드의 해상도를 설정
            imageDimension = sizesForStream[sizeIndex];
            videoDimension = sizesForStream[sizeIndex];


            //캡처를 위한 리더기 생성
            imageReader = ImageReader.newInstance(
                    imageDimension.getWidth(),
                    imageDimension.getHeight(),
                    ImageFormat.JPEG, 1);

            //동영상을 저장하기 위한 리더기 생성
            mediaRecorder=new MediaRecorder();

            //기본폴더 준비 과정
            initFileFolder();


        }catch (CameraAccessException cameraAccessException)
        {
            //카메라 가 준비되지 않을 떄 발생할 수 있는 오류
            return false;
        }



        return true;
    }

    private void initSensor(){
        mSensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
        mAccSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mDegrees = 0;

        sensorEventListener=new SensorEventListener() {

            @Override
            public void onSensorChanged(SensorEvent event) {

                if(event.sensor.getType()==Sensor.TYPE_ACCELEROMETER)
                {
                    float x = event.values[0];
                    float y = event.values[1];
                    // samsung s8+ x,y 에 따른 회전 각

                    if(x > 5 && y < 5)mDegrees = 0;    //오른쪽
                    else if(x < -5 && y > -5)mDegrees = 180;  //왼쪽
                    else if(x > -5 && y > 5)mDegrees = 90;  //세로 정방향
                    else if(x < 5 && y < -5)mDegrees = -90;   //세로 뒤집기
                }

            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };
        mSensorManager.registerListener(sensorEventListener, mAccSensor, SensorManager.SENSOR_DELAY_UI);
    }






    //종료
    public void close(){
        test.serverStop();
        if(cameraPreviewSession!=null){
            cameraPreviewSession.close();
            cameraPreviewSession=null;

        }
        if(cameraCaptureSession!=null){
            cameraCaptureSession.close();
            cameraCaptureSession=null;
        }
        if(cameraVideoSession!=null){
            cameraVideoSession.close();
            cameraVideoSession=null;
        }
        if(imageReader!=null){
            imageReader.close();
            imageReader=null;
        }
        if(mSensorManager!=null){
            mSensorManager.unregisterListener(sensorEventListener);
            mSensorManager=null;
        }
        if(cameraDevice!=null) {
            cameraDevice.close();
            cameraDevice=null;
        }
    }



    //카메라를 뷰어에 띄우기 위해 카메라를 실행하는 함수
    public void openCamera() {
        //CameraManager manager =
        try {

            // 카메라를 사용할때 권한을 체크한다.
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(context, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                return;
            }

            cameraManager.openCamera(oneCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice cameraDevice) {
                    CameraModule.this.cameraDevice = cameraDevice;
                    showCameraPreview();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                    cameraDevice.close();
                }

                @Override
                public void onError(@NonNull CameraDevice cameraDevice, int errorCode) {
                    Log.e("cklee", "MMM errorCode = " + errorCode);
                    cameraDevice.close();
                    CameraModule.this.cameraDevice = null;
                }
            }, null);





        } catch (CameraAccessException e) {
            Log.e("cklee", "MMM openCamera ", e);
        }
    }

    //textureView 에 프리뷰를 띄우는 함수
    public void showCameraPreview() {
        try {
            //카메라 세션이 사용중이면 겹치지 않게 지워준다.
            closeCameraPreviewSession();
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Log.d("cklee","확인용 w:"+ imageDimension.getWidth()+" h:"+ imageDimension.getHeight());

            Surface textureViewSurface = new Surface(texture);

            CaptureRequest.Builder captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            captureRequest.addTarget(textureViewSurface);
            captureRequest.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            //textureView Surface 는 textureView로 이루어진 List..?
            //미리보기 창을 실행하는 조건을 작성한다.
            cameraDevice.createCaptureSession(Arrays.asList(textureViewSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            CameraModule.this.cameraPreviewSession= session;
                            try {
                                //화면에 연속적으로 그리겠다 이말임
                                session.setRepeatingRequest(captureRequest.build(), null, null);
                            } catch (CameraAccessException cameraAccessException) {
                                cameraAccessException.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e("cklee", "MMM onConfigureFailed");
                        }
                    }, null);
        } catch (CameraAccessException e) {
            Log.e("cklee", "MMM showCameraPreview ", e);
        }
    }





    private void updatePreview() {
//        try {
//            cameraCaptureSession.setRepeatingRequest(captureRequest.build(), null, null);
//        } catch (CameraAccessException e) {
//            Log.e("cklee", "MMM updatePreview", e);
//        }
    }



    public void startRecordingVideo(){
        try{
            //카메라 세션이 사용중이면 겹치지 않게 지워준다.
            closeCameraVideoSession();
            setUpMediaRecorder();
            //캡처 세션을 만들기 전에 프리뷰를 위한 Surface를 준비..
            //레이아웃에 선언된 textureView로 부터 PreView를 가져옴
            SurfaceTexture surfaceTexture=textureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(imageDimension.getWidth(),imageDimension.getHeight());
            //미리보기 화면을 요청한다.위에 Surface를 타겠으로 한다.
            final CaptureRequest.Builder captureRequest=cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);


            ArrayList<Surface> surfaces=new ArrayList<Surface>();
           // Surface previewSurface=new Surface(surfaceTexture);
            //프리뷰활성화
          //  surfaces.add(previewSurface);
          //  captureRequest.addTarget(previewSurface);


         //   Surface 를 미디어 레코더에 올린다.
            Surface recorderSurface=mediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            captureRequest.addTarget(recorderSurface);

            //surfaces List를 매개 변수에 넣으면 리스트에 있는 ㅡSurface에 이미지 전송
            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    CameraModule.this.cameraVideoSession=session;
                    captureRequest.set(CaptureRequest.CONTROL_MODE,CameraMetadata.CONTROL_MODE_AUTO);
                    try {
                        session.setRepeatingRequest(captureRequest.build(),null,null);

                        /*
                        객체이 이미지를 삽입한다.
                        picture.setImageResource(R.drawable.ic.recodingstop
                        isVideoRecording=true;
                         */
                        mediaRecorder.start();
                        isVideoRecording=true;

                    } catch (Exception cameraAccessException) {
                        cameraAccessException.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(context,"Failed",Toast.LENGTH_SHORT).show();
                }
            },null);



        }catch(Exception e)
        {

        }
    }

    public void closeCameraPreviewSession(){
        if(cameraPreviewSession!=null){
            cameraPreviewSession.close();
            cameraPreviewSession=null;
        }
    }
    public void closeCameraCaptureSession(){
        if(cameraCaptureSession!=null){
            cameraCaptureSession.close();
            cameraCaptureSession=null;
        }
    }
    public void closeCameraVideoSession(){
        if(cameraVideoSession!=null){
            cameraVideoSession.close();
            cameraVideoSession=null;
        }
    }
    private void setUpMediaRecorder(){
        //마이크set
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        //
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);


        File file=new File(
                filePath+
                        fileFolder+
                        "video/"+
                        dateFileFolder.format(new Date(System.currentTimeMillis()))+
                        "/");
        //당일 폴더가 없으면 생성하기
        if(!file.exists())
            file.mkdirs();
        file=new File(file.getPath()+"/"+
                fileName.format(new Date(System.currentTimeMillis())) +
                ".mp4");
        mediaRecorder.setOutputFile(file);

        mediaRecorder.setVideoEncodingBitRate(10000000);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoSize(videoDimension.getWidth(),videoDimension.getHeight());
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setMaxDuration(5000);


        //mediaRecorder.set

        mediaRecorder.setOrientationHint((int)mDegrees);
        try {
            mediaRecorder.prepare();
        } catch (IOException exception) {
            Log.d("cklee","미디어 레코더 준비 불가");
        }

    }
    public void stopRecordingVideo(){
        //버튼 이미지를 바꾼다.
        //btn_takePicture.setImageResource(R.draw reco)..
        if(isVideoRecording)
        {
            isVideoRecording=false;
            mediaRecorder.stop();
            mediaRecorder.reset();

        }else
        {
            Log.d("cklee","null입니다.");
        }

        Toast.makeText(context,"동영상이 촬여되었어요",Toast.LENGTH_SHORT).show();

        //openCamera();
    }


    public void takePicture(){
        try {
            //카메라 세션이 사용중이면 겹치지 않게 지워준다.
            closeCameraCaptureSession();
            //Surface를 가진곳으로 사진을 보낼 곳을 List로 추가하는데
            //이미지리더기는 저장용으로 텍스처뷰는 화면출력용으로 보낸다.
            //ArrayList<Surface> outputSurface = new ArrayList<Surface>();
            //outputSurface.add(imageReader.getSurface());
            //outputSurface.add(new Surface(textureView.getSurfaceTexture()));
            //캡처 요청 생성기 생성
            final CaptureRequest.Builder captureBuilder =
            //      mCaptureRequestBuilder=
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            //이미지 리더기 객체를 캡처에 요청타겟 지정
            captureBuilder.addTarget(imageReader.getSurface());
//            mCaptureRequestBuilder.addTarget(imageReader.getSurface());
            //캡처생성기의 모드 설정
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
//            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE,CameraMetadata.CONTROL_MODE_AUTO);


            //이미지리더에서 이미지를 읽기 위한 준비가 되었을 때 방식
            ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                        Image image = reader.acquireLatestImage();

                        //이미지를 버퍼로 뽑아낸다.
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        image.close();
                        //바이트를 기반으로 비트맵을 만든다.
                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);

                        //90도 돌리기위해 orientation에 따른 매트릭스를 생성하고
                        Matrix matrix = new Matrix();
                        matrix.postRotate(mDegrees);
                        //회전한 비트맵을 만든다.
                        Bitmap rotatebitmap = Bitmap.createBitmap(
                                bitmap,
                                0,
                                0,
                                bitmap.getWidth(), bitmap.getHeight(), matrix, false);

                        //mQueue.add(rotatebitmap);

                    try {
                        //이미지 파일을 버퍼로 읽고 적어나간다.


                        OutputStream outputStream = null;
                        File file = new File(filePath +
                                fileFolder +
                                "image/" +
                            //    fileName.format(new Date(System.currentTimeMillis())) +
                                "1"+
                                ".jpg");

                        try {
                            outputStream = new FileOutputStream(file);
                            //outputStream.write(bytes);
                            rotatebitmap.compress(Bitmap.CompressFormat.JPEG,100,outputStream);
                            //mQueue.poll().compress(Bitmap.CompressFormat.JPEG, 100, outputStream);

//                            ByteArrayOutputStream stream = new ByteArrayOutputStream() ;
//                            bitmap.compress( CompressFormat.JPEG, 100, stream) ;
//                            byte[] byteArray = stream.toByteArray() ;
                            outputStream.flush();
                        } finally {

                            //파일이 있는지 확인
                            Uri uri = Uri.fromFile(file);
                            Log.d("cklee", "URI를 확인한다." + uri);




                                /*
                                이미지 프리뷰공간에 보여준다
                                imgpreviewObject.setImageBitmap(rotateBitmap);
                                 */
                                /*
                                리사이클러 뷰 갤러리로 보내줄 uriList에 찍은 사진의 uri 를 넣어준다.
                                uriList.add(0,uri.toString());
                                 */
                            //출력스트림 닫기
                            outputStream.close();

                        }


                    } catch (FileNotFoundException fileNotFoundException) {
                        Log.d("cklee", "이미지 경로 못 찾음");
                    } catch (IOException exception) {
                        Log.d("cklee", "이미지 IO 오류");
                    }
                }


            };

            HandlerThread thread = new HandlerThread("CameraPicture");
            thread.start();
            final Handler backgroudHandler = new Handler(thread.getLooper());



            //이미지 리더 객체에 만든 리스너를 넣어 사용가능 하면 저장할 수 있도록 한다.
            imageReader.setOnImageAvailableListener(imageAvailableListener, backgroudHandler);


            //세션을 만들어 해당 Surface로 캡처 정보를 보낸다.
            cameraDevice.createCaptureSession(Arrays.asList(imageReader.getSurface()),//outputSurface
                    new CameraCaptureSession.StateCallback() {
                //정상 동작시
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    CameraModule.this.cameraCaptureSession= session;
                    try{
                        session.capture(captureBuilder.build(),null,backgroudHandler);


                        } catch (CameraAccessException cameraAccessException) {
                            cameraAccessException.printStackTrace();
                        }


                }
                //실패시 동작은 안한다.
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {}
            },backgroudHandler);


        }catch(CameraAccessException exception)
        {
            Log.d("cklee","카메라 접근 권한 에러");
        }

    }

    public void showCameraPreview2() {
        try {
            //카메라 세션이 사용중이면 겹치지 않게 지워준다.
            closeCameraPreviewSession();
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Log.d("cklee","확인용 w:"+ imageDimension.getWidth()+" h:"+ imageDimension.getHeight());

            Surface textureViewSurface = new Surface(texture);

            CaptureRequest.Builder captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            captureRequest.addTarget(textureViewSurface);
            captureRequest.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            //textureView Surface 는 textureView로 이루어진 List..?
            //미리보기 창을 실행하는 조건을 작성한다.
            cameraDevice.createCaptureSession(Arrays.asList(textureViewSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            CameraModule.this.cameraPreviewSession= session;
                            try {
                                //화면에 연속적으로 그리겠다 이말임
                                session.setRepeatingRequest(captureRequest.build(), null, null);

                            } catch (CameraAccessException cameraAccessException) {
                                cameraAccessException.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e("cklee", "MMM onConfigureFailed");
                        }
                    }, null);
        } catch (CameraAccessException e) {
            Log.e("cklee", "MMM showCameraPreview ", e);
        }
    }
    public void takePictureStremming(){

        test.serverOn();

        try {
            //카메라 세션이 사용중이면 겹치지 않게 지워준다.
            closeCameraCaptureSession();
            //Surface를 가진곳으로 사진을 보낼 곳을 List로 추가하는데
            //이미지리더기는 저장용으로 텍스처뷰는 화면출력용으로 보낸다.
            ArrayList<Surface> outputSurface = new ArrayList<Surface>();
            outputSurface.add(imageReader.getSurface());
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Log.d("cklee","확인용 w:"+ imageDimension.getWidth()+" h:"+ imageDimension.getHeight());

            Surface textureViewSurface = new Surface(texture);
            outputSurface.add(textureViewSurface);

            //캡처 요청 생성기 생성
          //  final CaptureRequest.Builder captureBuilder =
                    //mCaptureRequestBuilder=
            //        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            //이미지 리더기 객체를 캡처에 요청타겟 지정
            captureBuilder.addTarget(imageReader.getSurface());
            captureBuilder.addTarget(textureViewSurface);
//            mCaptureRequestBuilder.addTarget(imageReader.getSurface());
            //캡처생성기의 모드 설정
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

//            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE,CameraMetadata.CONTROL_MODE_AUTO);


            Image image;

            //이미지리더에서 이미지를 읽기 위한 준비가 되었을 때 방식
            ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
                ByteBuffer byteBuffer;
                Image image;
                Bitmap bitmap;
                Bitmap rotatebitmap;
                byte[] bytes;

                @Override
                public void onImageAvailable(ImageReader reader) {
                    //Image image = reader.acquireNextImage();

                    //단순 이미지 송출
                    image=reader.acquireNextImage();
                    byteBuffer=image.getPlanes()[0].getBuffer();
                    bytes=new byte[byteBuffer.capacity()];
                    byteBuffer.get(bytes);
                    test.setJdata(bytes);
                    image.close();


            /*
                    //회전 이미지 송출
                    image=reader.acquireNextImage();
                    byteBuffer=image.getPlanes()[0].getBuffer();
                    bytes=new byte[byteBuffer.capacity()];
                    byteBuffer.get(bytes);
                    bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
                    Matrix matrix=new Matrix();
                    matrix.postRotate(mDegrees);
                    rotatebitmap = Bitmap.createBitmap(
                            bitmap,
                            0,
                            0,
                            bitmap.getWidth(), bitmap.getHeight(), matrix, false);
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    rotatebitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
                    test.setJdata(stream.toByteArray());
                    try {
                        stream.close();
                    } catch (IOException exception) {
                        exception.printStackTrace();
                    }
                    image.close();
                */


/*
                    //이미지를 버퍼로 뽑아낸다.
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.capacity()];
                    buffer.get(bytes);
                    test.setJdata(bytes);
                    image.close();
  */

                    /*
                    //바이트를 기반으로 비트맵을 만든다.
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);


                    //90도 돌리기위해 orientation에 따른 매트릭스를 생성하고
                    Matrix matrix = new Matrix();
                    matrix.postRotate(mDegrees);
                    //회전한 비트맵을 만든다.
                    Bitmap rotatebitmap = Bitmap.createBitmap(
                            bitmap,
                            0,
                            0,
                            bitmap.getWidth(), bitmap.getHeight(), matrix, false);
                    //mQueue.add(rotatebitmap);
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    rotatebitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    byte[] byteArray = stream.toByteArray();
                    test.setJdata(byteArray);
                    try {
                        stream.close();
                    } catch (IOException exception) {
                        exception.printStackTrace();
                    }

                     */

                }
            };



            //이미지 리더 객체에 만든 리스너를 넣어 사용가능 하면 저장할 수 있도록 한다.
            imageReader.setOnImageAvailableListener(imageAvailableListener, null);

            //세션을 만들어 해당 Surface로 캡처 정보를 보낸다.
            cameraDevice.createCaptureSession(outputSurface,//outputSurface
                    new CameraCaptureSession.StateCallback() {
                        //정상 동작시
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            CameraModule.this.cameraCaptureSession= session;

                            try{
                                session.setRepeatingRequest(captureBuilder.build(),null,null);
                            } catch (CameraAccessException cameraAccessException) {
                                cameraAccessException.printStackTrace();
                            }
                        }
                        //실패시 동작은 안한다.
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {}
                    },null);


        }catch(CameraAccessException exception)
        {
            Log.d("cklee","카메라 접근 권한 에러");
        }

    }


















    private void initFileFolder(){

            filePath=Environment.getExternalStorageDirectory().toString();

        fileFolder="/MyCCTV/";

        File file=new File(filePath+fileFolder+"image");
        if(!file.exists())
        {
            file.mkdirs();
        }
        file=new File(filePath+fileFolder+"video");
        if(!file.exists())
        {
            file.mkdirs();
        }



    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(context, "Permission denied", Toast.LENGTH_LONG).show();
            }
        } else {
           // openCamera();
        }
    }










    //outputSurface 를 이용한 이미지 다수객체에 갱신
    private void TesttakePicture(){

//        try {
//            //카메라 세션이 사용중이면 겹치지 않게 지워준다.
//            closeCameraPreviewSession();
//            //Surface를 가진곳으로 사진을 보낼 곳을 List로 추가하는데
//            //이미지리더기는 저장용으로 텍스처뷰는 화면출력용으로 보낸다.
//            ArrayList<Surface> outputSurface = new ArrayList<Surface>();
//            outputSurface.add(imageReader.getSurface());
//            outputSurface.add(new Surface(textureView.getSurfaceTexture()));
//
//            //캡처 요청 생성기 생성
//            final CaptureRequest.Builder captureBuilder =
//                    //      mCaptureRequestBuilder=
//                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
//            //이미지 리더기 객체를 캡처에 요청타겟 지정
//            captureBuilder.addTarget(imageReader.getSurface());
////            mCaptureRequestBuilder.addTarget(imageReader.getSurface());
//            //캡처생성기의 모드 설정
//            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
////            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE,CameraMetadata.CONTROL_MODE_AUTO);
//
//
//
//            //이미지리더에서 이미지를 읽기 위한 준비가 되었을 때 방식
//            ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
//                @Override
//                public void onImageAvailable(ImageReader reader) {
//                    Image image = reader.acquireLatestImage();
//
//                    //이미지를 버퍼로 뽑아낸다.
//                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
//                    byte[] bytes = new byte[buffer.capacity()];
//                    buffer.get(bytes);
//                    image.close();
//                    //바이트를 기반으로 비트맵을 만든다.
//                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
//
//                    //90도 돌리기위해 orientation에 따른 매트릭스를 생성하고
//                    Matrix matrix = new Matrix();
//                    matrix.postRotate(mDegrees);
//                    //회전한 비트맵을 만든다.
//                    Bitmap rotatebitmap = Bitmap.createBitmap(
//                            bitmap,
//                            0,
//                            0,
//                            bitmap.getWidth(), bitmap.getHeight(), matrix, false);
//
//                    //mQueue.add(rotatebitmap);
//
//                    try {
//                        //이미지 파일을 버퍼로 읽고 적어나간다.
//
//
//                        OutputStream outputStream = null;
//                        File file = new File(filePath +
//                                fileFolder +
//                                "image/" +
//                                sdf.format(new Date(System.currentTimeMillis())) +
//                                ".jpg");
//
//                        try {
//                            outputStream = new FileOutputStream(file);
//                            //outputStream.write(bytes);
//                            rotatebitmap.compress(Bitmap.CompressFormat.JPEG,100,outputStream);
//                            //mQueue.poll().compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
//                            outputStream.flush();
//                        } finally {
//
//                            //파일이 있는지 확인
//                            Uri uri = Uri.fromFile(file);
//                            Log.d("cklee", "URI를 확인한다." + uri);
//
//
//
//
//                                /*
//                                이미지 프리뷰공간에 보여준다
//                                imgpreviewObject.setImageBitmap(rotateBitmap);
//                                 */
//                                /*
//                                리사이클러 뷰 갤러리로 보내줄 uriList에 찍은 사진의 uri 를 넣어준다.
//                                uriList.add(0,uri.toString());
//                                 */
//                            //출력스트림 닫기
//                            outputStream.close();
//
//                        }
//
//
//                    } catch (FileNotFoundException fileNotFoundException) {
//                        Log.d("cklee", "이미지 경로 못 찾음");
//                    } catch (IOException exception) {
//                        Log.d("cklee", "이미지 IO 오류");
//                    }
//                }
//
//
//            };
//
//
//
//
//
//
//
//
//
//
//
//
//
////            HandlerThread thread = new HandlerThread("CameraPicture");
////            thread.start();
////            final Handler backgroudHandler = new Handler(thread.getLooper());
//
//
//
//            //이미지 리더 객체에 만든 리스너를 넣어 사용가능 하면 저장할 수 있도록 한다.
//            imageReader.setOnImageAvailableListener(imageAvailableListener, null);
//            CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
//                //여러가지 오버라이딩이 있지만 캡처가 완료되었을 때 콜백 문구를 만든다.
//                @Override
//                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
//                    super.onCaptureCompleted(session, request, result);
//                    Toast.makeText(context, "사진을 촬영했어요", Toast.LENGTH_SHORT).show();
//                    //카메라 프리뷰 세션을 다시 생성한다.??
//                        /*
//                            createCameraPreviewSession();
//                         */
//                    //       openCamera();
//
//                };
//            };
//
//
//            //세션을 만들어 해당 Surface로 캡처 정보를 보낸다.
//            cameraDevice.createCaptureSession(outputSurface,//outputSurface
//                    new CameraCaptureSession.StateCallback() {
//                        //정상 동작시
//                        @Override
//                        public void onConfigured(@NonNull CameraCaptureSession session) {
//                            try{
//                                session.capture(captureBuilder.build(),captureListener,null);
//                                showCameraPreview();
//
//                            } catch (CameraAccessException cameraAccessException) {
//                                cameraAccessException.printStackTrace();
//                            }
//
//
//                        }
//                        //실패시 동작은 안한다.
//                        @Override
//                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {}
//                    },null);
//
//
//        }catch(CameraAccessException exception)
//        {
//            Log.d("cklee","카메라 접근 권한 에러");
//        }

    }

}
