package com.example.vcam;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import android.annotation.SuppressLint;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookMain implements IXposedHookLoadPackage {
    public static Surface msurf;
    public static SurfaceTexture msurftext;
    public static MediaPlayer mMedia;
    public static SurfaceTexture virtual_st;
    public static Camera reallycamera;

    public static Camera data_camera;
    public static byte[] data_buffer;
    public static int mhight;
    public static int mwidth;

    public static Camera onedata_camera;
    public static byte[] onedata_buffer;
    public static int onemhight;
    public static int onemwidth;

    public static Surface c2_ori_Surf ;
    public static Surface c2_vir_Surf;
    public static MediaPlayer c2_player;
    public static CaptureRequest.Builder c2_builder ;
    public static SurfaceTexture c2_virt_st;
    public static ImageReader c2_image_reader;

    public static Class callback_calss;

    public Handler mHandler;

    public static int repeat_count;


    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam)  throws Exception {
        @SuppressLint("SdCardPath") File file = new File("/sdcard/DCIM/Camera/virtual.mp4");
        if (!file.exists()) {
            return;
        }
        Class cameraclass = XposedHelpers.findClass("android.hardware.Camera", lpparam.classLoader);
        XposedHelpers.findAndHookMethod(cameraclass, "setPreviewTexture", 	android.graphics.SurfaceTexture.class, new XC_MethodHook() {
            @SuppressLint("SdCardPath")
            @Override
            protected void beforeHookedMethod(MethodHookParam param){

                if (reallycamera != null && reallycamera.equals((Camera) param.thisObject)){
                    param.args[0]= HookMain.virtual_st;
                    XposedBridge.log("发现重复" + reallycamera.toString());
                    return;
                }

                reallycamera = (Camera) param.thisObject;
                HookMain.msurftext = (SurfaceTexture) param.args[0];


                if (HookMain.virtual_st == null){
                    HookMain.virtual_st = new SurfaceTexture(10);
                }else{
                    HookMain.virtual_st.release();
                    HookMain.virtual_st = new SurfaceTexture(10);
                }
                param.args[0]= HookMain.virtual_st;

                if (HookMain.msurf == null){
                    HookMain.msurf = new Surface(HookMain.msurftext);
                }else {
                    HookMain.msurf.release();
                    HookMain.msurf = new Surface(HookMain.msurftext);
                }

                if (HookMain.mMedia == null) {
                    HookMain.mMedia = new MediaPlayer();
                }else{
                    HookMain.mMedia.release();
                    HookMain.mMedia = new MediaPlayer();
                }

                HookMain.mMedia.setSurface(HookMain.msurf);

                HookMain.mMedia.setVolume(0, 0);
                HookMain.mMedia.setLooping(true);

                HookMain.mMedia.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        HookMain.mMedia.start();
                    }
                });

                try {
                    HookMain.mMedia.setDataSource("/sdcard/DCIM/Camera/virtual.mp4");
                    HookMain.mMedia.prepare();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        });


        XposedHelpers.findAndHookMethod("android.hardware.camera2.CaptureRequest.Builder" ,lpparam.classLoader, "addTarget",android.view.Surface.class, new XC_MethodHook() {
            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @SuppressLint("SdCardPath")
            @Override
            protected void beforeHookedMethod(MethodHookParam param){

                if (HookMain.c2_builder != null && HookMain.c2_builder.equals((CaptureRequest.Builder) param.thisObject)){
                    param.args[0]= HookMain.c2_vir_Surf;
                    XposedBridge.log("发现重复" + HookMain.c2_builder.toString());
                    return;
                }

                HookMain.c2_builder = (CaptureRequest.Builder) param.thisObject;
                HookMain.c2_ori_Surf = (Surface) param.args[0];

                if (HookMain.c2_virt_st == null){
                    HookMain.c2_virt_st = new SurfaceTexture(10);
                }else{
                    HookMain.c2_virt_st.release();
                    HookMain.c2_virt_st = new SurfaceTexture(10);
                }

                if (HookMain.c2_vir_Surf == null){
                    HookMain.c2_vir_Surf = new Surface(c2_virt_st);
                }else{
                    HookMain.c2_vir_Surf.release();
                    HookMain.c2_vir_Surf = new Surface(c2_virt_st);
                }

                if (HookMain.c2_image_reader == null){
                    HookMain.c2_image_reader = ImageReader.newInstance(1920,720,ImageFormat.YUV_420_888,5);
                }

                param.args[0]=HookMain.c2_image_reader.getSurface();

                if (HookMain.c2_player == null) {
                    HookMain.c2_player = new MediaPlayer();
                }else{
                    HookMain.c2_player.release();
                    HookMain.c2_player = new MediaPlayer();
                }

                HookMain.c2_player.setSurface(HookMain.c2_ori_Surf);


                HookMain.c2_player.setVolume(0, 0);
                HookMain.c2_player.setLooping(true);

                HookMain.c2_player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    public void onPrepared(MediaPlayer mp) {
                        HookMain.c2_player.start();
                    }
                });


                try {
                    HookMain.c2_player.setDataSource("/sdcard/DCIM/Camera/virtual.mp4");
                    HookMain.c2_player.prepare();
                } catch (IOException e) {
                    e.printStackTrace();
                }



            }
        });


        XposedHelpers.findAndHookMethod("android.hardware.Camera" ,lpparam.classLoader, "setPreviewCallbackWithBuffer", Camera.PreviewCallback.class, new XC_MethodHook() {
            @SuppressLint("SdCardPath")
            @Override
            protected void beforeHookedMethod(MethodHookParam param){
                try {
                    if (param.args[0] != null) {
                        XposedBridge.log("创建了带缓存回调 类：" + param.args[0].toString());
                        callback_calss = param.args[0].getClass();
                    } else {
                        callback_calss = null;
                    }
                }catch (Exception eee){
                    XposedBridge.log("出错："+eee.toString());
                    callback_calss = null;
                }
            }
        });

        XposedHelpers.findAndHookMethod("android.hardware.Camera" ,lpparam.classLoader, "setPreviewCallback", Camera.PreviewCallback.class, new XC_MethodHook() {
            @SuppressLint("SdCardPath")
            @Override
            protected void beforeHookedMethod(MethodHookParam param){
                try {
                    if (param.args[0] != null) {
                        XposedBridge.log("创建了无缓存回调 类：" + param.args[0].toString());
                        callback_calss = param.args[0].getClass();
                    } else {
                        callback_calss = null;
                    }
                }catch (Exception eee){
                    XposedBridge.log("出错："+eee.toString());
                    callback_calss = null;
                }
            }
        });

        XposedHelpers.findAndHookMethod("android.hardware.Camera" ,lpparam.classLoader, "setOneShotPreviewCallback", Camera.PreviewCallback.class, new XC_MethodHook() {
            @SuppressLint("SdCardPath")
            @Override
            protected void beforeHookedMethod(MethodHookParam param){
                try {
                    if (param.args[0] != null) {
                        XposedBridge.log("创建了一帧回调 类：" + param.args[0].toString());
                        callback_calss = param.args[0].getClass();
                    } else {
                        callback_calss = null;
                    }
                }catch (Exception eee){
                    XposedBridge.log("出错："+eee.toString());
                    callback_calss = null;
                }
            }
        });

        XposedHelpers.findAndHookMethod("android.hardware.Camera" ,lpparam.classLoader, "takePicture", Camera.ShutterCallback.class,Camera.PictureCallback.class,Camera.PictureCallback.class,Camera.PictureCallback.class, new XC_MethodHook() {
            @SuppressLint("SdCardPath")
            @Override
            protected void afterHookedMethod(MethodHookParam param){
                XposedBridge.log("4参数拍照");
                if (param.args[1] == null){
                    process_a_shot_jpeg(param,3);
                }else {
                    process_a_shot_YUV(param);
                }
            }
        });

        XposedHelpers.findAndHookMethod("android.hardware.Camera" ,lpparam.classLoader, "stopPreview", new XC_MethodHook() {
            @SuppressLint("SdCardPath")
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                XposedBridge.log("终止预览：" + param.thisObject.getClass().toString());
                callback_calss = null;
            }

        });

        XposedHelpers.findAndHookMethod("android.hardware.Camera" ,lpparam.classLoader, "takePicture", Camera.ShutterCallback.class,Camera.PictureCallback.class,Camera.PictureCallback.class, new XC_MethodHook() {
            @SuppressLint("SdCardPath")
            @Override
            protected void afterHookedMethod(MethodHookParam param){
                XposedBridge.log("3参数拍照");
                if (param.args[1] == null){
                    process_a_shot_jpeg(param,2);
                }else {
                    process_a_shot_YUV(param);
                }
            }
        });

        if (HookMain.callback_calss != null) {
            XposedHelpers.findAndHookMethod(callback_calss, "onPreviewFrame", byte[].class, android.hardware.Camera.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam paramd) throws Throwable {
                    Camera localcam = (android.hardware.Camera) paramd.args[1];
                    File testfile = new File("/sdcard/DCIM/Camera/bmp/" + String.valueOf(repeat_count+1) + ".bmp");
                    if (!file.exists()){
                        repeat_count = 1000;
                    }
                    if (localcam.equals(data_camera)) {
                        repeat_count += 1;
                        try {
                            byte[] bt = (byte[]) paramd.args[0];
                            int lt = 0;
                            lt = bt.length;
                            byte[] input = new byte[lt];
                            input = getYUVByBitmap(getBMP("/sdcard/DCIM/Camera/bmp/" + String.valueOf(repeat_count) + ".bmp"));
                            paramd.args[0] = input;
                        } catch (Exception eee) {
                            XposedBridge.log(eee.toString());
                        }
                    } else {
                        //XposedBridge.log("初始化");
                        repeat_count = 1000;
                        HookMain.data_camera = (android.hardware.Camera) paramd.args[1];
                        byte[] bt = (byte[]) paramd.args[0];
                        int lt = 0;
                        lt = bt.length;
                        HookMain.data_buffer = new byte[lt];
                        mwidth = data_camera.getParameters().getPreviewSize().width;
                        mhight = data_camera.getParameters().getPreviewSize().height;
                        XposedBridge.log("预览回调初始化：宽：" + String.valueOf(mwidth) + "高：" + String.valueOf(mhight));

                        byte[] bta = (byte[]) paramd.args[0];
                        int lta = 0;
                        lt = bt.length;
                        byte[] inputa = new byte[lt];
                        inputa = getYUVByBitmap(getBMP("/sdcard/DCIM/Camera/bmp/" + String.valueOf(repeat_count) + ".bmp"));
                        paramd.args[0] = inputa;

                    /*if (data_imagereader!=null){
                        data_imagereader = null;
                    }
                    data_imagereader = ImageReader.newInstance(mwidth,mhight, ImageFormat.YUV_420_888,20);
                    data_imagereader.setOnImageAvailableListener(aamOnImageAvailableListener,mHandler);
                    data_mediaplayer = new MediaPlayer();
                    data_mediaplayer.setSurface(data_imagereader.getSurface());
                    HookMain.data_mediaplayer.setVolume(0, 0);
                    HookMain.data_mediaplayer.setLooping(true);
                    HookMain.data_mediaplayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mp) {
                            HookMain.data_mediaplayer.start();
                        }
                    });
                    try {
                        XposedBridge.log("开始播放1111");
                        HookMain.data_mediaplayer.setDataSource("/sdcard/DCIM/Camera/virtual.mp4");
                        HookMain.data_mediaplayer.prepare();
                        XposedBridge.log("开始播放");
                    } catch (IOException e) {
                        e.printStackTrace();
                        XposedBridge.log(e.toString());
                    }*/
                    }

                }
            });
        }

    }

    public void process_a_shot_jpeg(XC_MethodHook.MethodHookParam param,int index){
        try{
            //XposedBridge.log("发现拍照raw:"+ param.args[1].toString());
            XposedBridge.log("第二个jpeg:"+param.args[index].toString());}catch (Exception eee){
            XposedBridge.log(eee.toString());

        }
        Class callback = param.args[index].getClass();

        XposedHelpers.findAndHookMethod(callback, "onPictureTaken", byte[].class, android.hardware.Camera.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam paramd) throws Throwable {
                try {
                    Camera loaclcam = (Camera) paramd.args[1];
                    onemwidth = loaclcam.getParameters().getPreviewSize().width;
                    onemhight = loaclcam.getParameters().getPreviewSize().height;
                    XposedBridge.log("JPEG拍照回调初始化：宽：" + String.valueOf(onemwidth) + "高：" + String.valueOf(onemhight));
                    Bitmap pict = getBMP("/sdcard/DCIM/Camera/bmp/1002.bmp");
                    ByteArrayOutputStream temp_array = new ByteArrayOutputStream();
                    pict.compress(Bitmap.CompressFormat.JPEG,100,temp_array);
                    byte[] jpeg_data = temp_array.toByteArray();
                    /*byte[] bt = (byte[]) paramd.args[0];
                    int lt = 0;
                    lt = bt.length;
                    byte[] inputa = new byte[lt];*/

                    paramd.args[0] = jpeg_data;
                }catch (Exception ee){
                    XposedBridge.log(ee.toString());
                }
            }
        });
    }

    public void process_a_shot_YUV(XC_MethodHook.MethodHookParam param){
        try{
            XposedBridge.log("发现拍照raw:"+ param.args[1].toString());
            //XposedBridge.log("第二个jpeg:"+param.args[3].toString());
            }catch (Exception eee){
            XposedBridge.log(eee.toString());

        }
        Class callback = param.args[1].getClass();

        XposedHelpers.findAndHookMethod(callback, "onPictureTaken", byte[].class, android.hardware.Camera.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam paramd) throws Throwable {
                try {
                    Camera loaclcam = (Camera) paramd.args[1];
                    onemwidth = loaclcam.getParameters().getPreviewSize().width;
                    onemhight = loaclcam.getParameters().getPreviewSize().height;
                    XposedBridge.log("YUV拍照回调初始化：宽：" + String.valueOf(onemwidth) + "高：" + String.valueOf(onemhight));
                    byte[] input = getYUVByBitmap(getBMP("/sdcard/DCIM/Camera/bmp/" + String.valueOf(repeat_count) + ".bmp"));
                    /*byte[] bt = (byte[]) paramd.args[0];
                    int lt = 0;
                    lt = bt.length;
                    byte[] inputa = new byte[lt];*/

                    paramd.args[0] = input;
                }catch (Exception ee){
                    XposedBridge.log(ee.toString());
                }
            }
        });
    }

/*
    public ImageReader.OnImageAvailableListener aamOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            XposedBridge.log("触发回调");
            //Image aimage;
            if (aimage != null){
                aimage.close();
            }
            XposedBridge.log("得到image");
            try{
                aimage = reader.acquireLatestImage();
                ByteBuffer buffer = aimage.getPlanes()[0].getBuffer();
                ByteBuffer buffer1 =  aimage.getPlanes()[1].getBuffer();
                ByteBuffer buffer2 =  aimage.getPlanes()[2].getBuffer();
                byte[] ee = new byte[buffer1.remaining()+buffer2.remaining()+buffer.remaining()];
                byte[] aa = new byte[buffer.remaining()];
                byte[] bb = new byte[buffer1.remaining()];
                byte[] cc =new byte[buffer2.remaining()];
                buffer.get(aa);
                buffer1.get(bb);
                buffer2.get(cc);
                System.arraycopy(aa, 0, ee, 0, aa.length);
                System.arraycopy(bb, 0, ee, aa.length, bb.length);
                System.arraycopy(cc, 0, ee, aa.length + bb.length, cc.length);
                //这里不知道为啥有BUG，应用闪退，却catch不到问题
                XposedBridge.log("得到buffer长度：" );
                //buffer.get(HookMain.data_buffer);
                XposedBridge.log("数据长度" + String.valueOf(ee.length));
                HookMain.data_buffer = ee;
                aimage.close();
            }catch (Exception ee){
                XposedBridge.log(ee.toString());
            }

        }
    };*/

    //以下代码来源：https://blog.csdn.net/jacke121/article/details/73888732

    public static byte[] rgb2YCbCr420(int[] pixels, int width, int height) {
        try {
            int len = width * height;
            // yuv格式数组大小，y亮度占len长度，u,v各占len/4长度。
            byte[] yuv = new byte[len * 3 / 2];
            int y, u, v;
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    int rgb = (pixels[i * width + j]) & 0x00FFFFFF;
                    int r = rgb & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = (rgb >> 16) & 0xFF;
                    // 套用公式
                    y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                    u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                    v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                    y = y < 16 ? 16 : (y > 255 ? 255 : y);
                    u = u < 0 ? 0 : (u > 255 ? 255 : u);
                    v = v < 0 ? 0 : (v > 255 ? 255 : v);
                    // 赋值
                    yuv[i * width + j] = (byte) y;
                    yuv[len + (i >> 1) * width + (j & ~1) + 0] = (byte) u;
                    yuv[len + +(i >> 1) * width + (j & ~1) + 1] = (byte) v;
                }
            }

            return yuv;
        } catch (Exception e) {
            XposedBridge.log(e.toString());
        }
        byte[] aa =new byte[0];
        return  aa;
    }

    private Bitmap getBMP(String file){
        try {
            FileInputStream is = new FileInputStream(file);
            Bitmap bmp = BitmapFactory.decodeStream(is);

            return bmp;
        } catch (Exception e) {
        }
        return null;
    }

    public static byte[] getYUVByBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int size = width * height;
        int pixels[] = new int[size];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        byte[] data = rgb2YCbCr420(pixels, width, height);
        return data;
    }
}


