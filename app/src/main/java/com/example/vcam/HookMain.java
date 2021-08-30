package com.example.vcam;


import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.view.Surface;

import androidx.annotation.RequiresApi;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

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
    public static volatile byte[] data_buffer;
    public static byte[] pic_buff_1;
    public static byte[] pic_buff_2;
    public static byte[] input;
    public static int mhight;
    public static int mwidth;
    public static Thread file_thred;
    public static Thread prepare_thred;
    public static int last_buffer_index;

    public static int onemhight;
    public static int onemwidth;

    public static Surface c2_ori_Surf;
    public static MediaPlayer c2_player;
    public static CaptureRequest.Builder c2_builder;
    public static ImageReader c2_image_reader;

    public static Class c2_state_callback;

    public static int repeat_count;

    public MediaCodec media_decode_obj;

    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Exception {
        @SuppressLint("SdCardPath") File file = new File("/sdcard/DCIM/Camera/virtual.mp4");
        if (file.exists()) {
            Class cameraclass = XposedHelpers.findClass("android.hardware.Camera", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(cameraclass, "setPreviewTexture", SurfaceTexture.class, new XC_MethodHook() {
                @SuppressLint("SdCardPath")
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args[0] == null) {
                        return;
                    }

                    if (reallycamera != null && reallycamera.equals((Camera) param.thisObject)) {
                        param.args[0] = HookMain.virtual_st;
                        XposedBridge.log("发现重复" + reallycamera.toString());
                        return;
                    } else {
                        XposedBridge.log("创建预览");
                    }

                    reallycamera = (Camera) param.thisObject;
                    HookMain.msurftext = (SurfaceTexture) param.args[0];


                    if (HookMain.virtual_st == null) {
                        HookMain.virtual_st = new SurfaceTexture(10);
                    } else {
                        HookMain.virtual_st.release();
                        HookMain.virtual_st = new SurfaceTexture(10);
                    }
                    param.args[0] = HookMain.virtual_st;

                    if (HookMain.msurf == null) {
                        HookMain.msurf = new Surface(HookMain.msurftext);
                    } else {
                        HookMain.msurf.release();
                        HookMain.msurf = new Surface(HookMain.msurftext);
                    }

                    if (HookMain.mMedia == null) {
                        HookMain.mMedia = new MediaPlayer();
                    } else {
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
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            XposedHelpers.findAndHookMethod("android.hardware.camera2.CameraManager", lpparam.classLoader, "openCamera", String.class, CameraDevice.StateCallback.class, Handler.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    c2_state_callback = param.args[1].getClass();
                    XposedBridge.log("1位参数初始化相机，类：" + c2_state_callback.toString());
                    process_camera2_init(c2_state_callback);
                }
            });
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            XposedHelpers.findAndHookMethod("android.hardware.camera2.CameraManager", lpparam.classLoader, "openCamera", String.class, Executor.class, CameraDevice.StateCallback.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    c2_state_callback = param.args[2].getClass();
                    XposedBridge.log("2位参数初始化相机，类：" + c2_state_callback.toString());
                    process_camera2_init(c2_state_callback);
                }
            });
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            XposedHelpers.findAndHookMethod("android.hardware.camera2.CaptureRequest.Builder", lpparam.classLoader, "addTarget", Surface.class, new XC_MethodHook() {
                @SuppressLint("SdCardPath")
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    File file = new File("/sdcard/DCIM/Camera/virtual.mp4");
                    File control_file = new File("/sdcard/DCIM/disable.jpg");
                    if (file.exists() && (!control_file.exists())) {

                        if (HookMain.c2_builder != null && HookMain.c2_builder.equals(param.thisObject)) {
                            param.args[0] = HookMain.c2_image_reader.getSurface();
                            XposedBridge.log("发现重复" + HookMain.c2_builder.toString());
                            return;
                        } else {
                            XposedBridge.log("创建C2预览");
                        }
                        HookMain.c2_builder = (CaptureRequest.Builder) param.thisObject;
                        HookMain.c2_ori_Surf = (Surface) param.args[0];

                        if (HookMain.c2_image_reader == null) {
                            HookMain.c2_image_reader = ImageReader.newInstance(1280, 720, ImageFormat.YUV_420_888, 5);
                        }

                        param.args[0] = HookMain.c2_image_reader.getSurface();

                        if (HookMain.c2_player == null) {
                            HookMain.c2_player = new MediaPlayer();
                        } else {
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
                }
            });
        }


        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "setPreviewCallbackWithBuffer", Camera.PreviewCallback.class, new XC_MethodHook() {
            @SuppressLint("SdCardPath")
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0] != null) {
                    process_callback(param);
                }
            }
        });

        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "addCallbackBuffer", byte[].class, new XC_MethodHook() {
            @SuppressLint("SdCardPath")
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0] != null) {
                    param.args[0] = new byte[((byte[]) param.args[0]).length];
                }
            }
        });

        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "setPreviewCallback", Camera.PreviewCallback.class, new XC_MethodHook() {
            @SuppressLint("SdCardPath")
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0] != null) {
                    process_callback(param);
                }
            }
        });

        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "setOneShotPreviewCallback", Camera.PreviewCallback.class, new XC_MethodHook() {
            @SuppressLint("SdCardPath")
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0] != null) {
                    process_callback(param);
                }
            }
        });

        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "takePicture", Camera.ShutterCallback.class, Camera.PictureCallback.class, Camera.PictureCallback.class, Camera.PictureCallback.class, new XC_MethodHook() {
            @SuppressLint("SdCardPath")
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                XposedBridge.log("4参数拍照");
                if (param.args[1] == null) {
                    process_a_shot_jpeg(param, 3);
                } else {
                    process_a_shot_YUV(param);
                }
            }
        });

        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "takePicture", Camera.ShutterCallback.class, Camera.PictureCallback.class, Camera.PictureCallback.class, new XC_MethodHook() {
            @SuppressLint("SdCardPath")
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                XposedBridge.log("3参数拍照");
                if (param.args[1] == null) {
                    process_a_shot_jpeg(param, 2);
                } else {
                    process_a_shot_YUV(param);
                }
            }
        });

        XposedHelpers.findAndHookMethod("android.media.MediaRecorder", lpparam.classLoader, "setCamera", Camera.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                XposedBridge.log("在录像，已打断");
                param.args[0] = null;
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @SuppressLint("SdCardPath")
    public void process_camera2_init(Class hooked_class) {
        File control_file = new File("/sdcard/DCIM/disable.jpg");
        if (control_file.exists()) {
            return;
        }
        File file = new File("/sdcard/DCIM/Camera/virtual.mp4");
        if (!file.exists()) {
            return;
        }
        XposedHelpers.findAndHookMethod(hooked_class, "onOpened", CameraDevice.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                XposedHelpers.findAndHookMethod(param.args[0].getClass(), "createCaptureSession", List.class, CameraCaptureSession.StateCallback.class, Handler.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (HookMain.c2_image_reader == null) {
                            HookMain.c2_image_reader = ImageReader.newInstance(1280, 720, ImageFormat.YUV_420_888, 5);
                        }
                        param.args[0] = Collections.singletonList(HookMain.c2_image_reader.getSurface());
                    }
                });
            }

        });
    }

    @SuppressLint("SdCardPath")
    public void process_a_shot_jpeg(XC_MethodHook.MethodHookParam param, int index) {
        try {
            XposedBridge.log("第二个jpeg:" + param.args[index].toString());
        } catch (Exception eee) {
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
                    XposedBridge.log("JPEG拍照回调初始化：宽：" + onemwidth + "高：" + onemhight + "对应的类：" + loaclcam.toString());
                    Bitmap pict = getBMP("/sdcard/DCIM/Camera/bmp/1000.bmp");
                    ByteArrayOutputStream temp_array = new ByteArrayOutputStream();
                    pict.compress(Bitmap.CompressFormat.JPEG, 100, temp_array);
                    byte[] jpeg_data = temp_array.toByteArray();
                    paramd.args[0] = jpeg_data;
                } catch (Exception ee) {
                    XposedBridge.log(ee.toString());
                }
            }
        });
    }

    @SuppressLint("SdCardPath")
    public void process_a_shot_YUV(XC_MethodHook.MethodHookParam param) {
        try {
            XposedBridge.log("发现拍照YUV:" + param.args[1].toString());
        } catch (Exception eee) {
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
                    XposedBridge.log("YUV拍照回调初始化：宽：" + onemwidth + "高：" + onemhight + "对应的类：" + loaclcam.toString());
                    input = getYUVByBitmap(getBMP("/sdcard/DCIM/Camera/bmp/1000.bmp"));
                    paramd.args[0] = input;
                } catch (Exception ee) {
                    XposedBridge.log(ee.toString());
                }
            }
        });
    }

    @SuppressLint("SdCardPath")
    public void process_callback(XC_MethodHook.MethodHookParam param) {
        Class nmb = param.args[0].getClass();
        XposedHelpers.findAndHookMethod(nmb, "onPreviewFrame", byte[].class, android.hardware.Camera.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam paramd) throws Throwable {
                Camera localcam = (android.hardware.Camera) paramd.args[1];
                if (localcam.equals(data_camera)) {
                    /*repeat_count += 1;
                    File test_file = new File("/sdcard/DCIM/Camera/bmp/" + String.valueOf(repeat_count) + ".bmp");
                    if (!test_file.exists()) {
                        repeat_count = 1000;
                    }
                    input = getYUVByBitmap(getBMP("/sdcard/DCIM/Camera/bmp/" + String.valueOf(repeat_count) + ".bmp"));
                    */
                    while (HookMain.data_buffer==null){

                    }
                    System.arraycopy(HookMain.data_buffer, 0, (byte[]) paramd.args[0], 0, Math.min(HookMain.data_buffer.length, ((byte[]) paramd.args[0]).length));
                    HookMain.data_buffer = null;
                } else {
                    repeat_count = 1000;
                    HookMain.data_camera = (android.hardware.Camera) paramd.args[1];
                    mwidth = data_camera.getParameters().getPreviewSize().width;
                    mhight = data_camera.getParameters().getPreviewSize().height;
                    XposedBridge.log("帧预览回调初始化：宽：" + mwidth + "高：" + mhight + "对应的类：" + data_camera.toString());
                    input = getYUVByBitmap(getBMP("/sdcard/DCIM/Camera/bmp/" + repeat_count + ".bmp"));
                    System.arraycopy(input, 0, (byte[]) paramd.args[0], 0, Math.min(input.length, ((byte[]) paramd.args[0]).length));
                    if (prepare_thred!=null){
                        prepare_thred.interrupt();
                        prepare_thred = null;
                    }
                    /*if (file_thred!=null){
                        file_thred.interrupt();
                        file_thred = null;
                    }*/
                    //HookMain.last_buffer_index = 2;
                    prepare_thred = new Thread(new Runnable() {
                        public void run() {
                            while (true) {
                                if (HookMain.data_buffer == null) {
                                    HookMain.repeat_count += 1;
                                    File test_file = new File("/sdcard/DCIM/Camera/bmp/" + HookMain.repeat_count + ".bmp");
                                    if (!test_file.exists()) {
                                        HookMain.repeat_count = 1000;
                                    }
                                    try {
                                        HookMain.data_buffer = getYUVByBitmap(getBMP("/sdcard/DCIM/Camera/bmp/" + repeat_count + ".bmp"));
                                    } catch (Throwable throwable) {
                                        XposedBridge.log("线程出错" + throwable.toString());
                                    }
                                }
                                /*if (HookMain.pic_buff_2 == null) {
                                    HookMain.repeat_count += 1;
                                    File test_file = new File("/sdcard/DCIM/Camera/bmp/" + HookMain.repeat_count + ".bmp");
                                    if (!test_file.exists()) {
                                        HookMain.repeat_count = 1000;
                                    }
                                    try {
                                        HookMain.pic_buff_2 = getYUVByBitmap(getBMP("/sdcard/DCIM/Camera/bmp/" + repeat_count + ".bmp"));
                                    } catch (Throwable throwable) {
                                        XposedBridge.log("线程出错" + throwable.toString());
                                    }
                                }*/

                            }
                        }
                    });

                   /* file_thred = new Thread(new Runnable() {
                        public void run() {
                            while (true) {
                                if (data_buffer == null) {
                                    if (last_buffer_index == 2) {
                                        data_buffer = pic_buff_1;
                                        pic_buff_1 = null;
                                        last_buffer_index = 1;
                                    } else {
                                        data_buffer = pic_buff_2;
                                        pic_buff_2 = null;
                                        last_buffer_index = 2;
                                    }
                                }
                            }
                        }
                    });*/

                    prepare_thred.start();
                    //file_thred.start();
                }

            }
        });
    }

    //以下代码来源：https://blog.csdn.net/jacke121/article/details/73888732
    private Bitmap getBMP(String file) throws Throwable {
        return BitmapFactory.decodeFile(file);
    }

    private static byte[] rgb2YCbCr420(int[] pixels, int width, int height) {
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
                y = y < 16 ? 16 : (Math.min(y, 255));
                u = u < 0 ? 0 : (Math.min(u, 255));
                v = v < 0 ? 0 : (Math.min(v, 255));
                // 赋值
                yuv[i * width + j] = (byte) y;
                yuv[len + (i >> 1) * width + (j & ~1)] = (byte) u;
                yuv[len + +(i >> 1) * width + (j & ~1) + 1] = (byte) v;
            }
        }
        return yuv;
    }

    private static byte[] getYUVByBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int size = width * height;
        int[] pixels = new int[size];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        return rgb2YCbCr420(pixels, width, height);
    }
}


