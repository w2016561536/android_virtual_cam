# android_virtual_cam

[简体中文](./README.md) | [繁體中文](./README_tc.md) | [English](./README_en.md)

A virtual camera based on Xposed

## DO NOT USE FOR ANY ILLEGAL PURPOSE, YOU NEED TO TAKE ALL RESPONSIBILITY AND CONSEQUENCE!

## Supported platform

- Android 5.0+

## Usage

1. Install this module , enable it in Xposed . Lsposed and other framework which have a scope list, you need to choose target app instead of System Framework.

2. In system Setting, authorize target to access local storage, and force stop the app. If the app does not request this permission, see step3.

3. open target app, if the app does not have the permission to access local storage. There will be a toast message showing that `Camera1` directory has been redirect to app's private directory `/[INTERNEL_STORAGE]/Android/data/[package_name]/files/Camera1/`. If there isn't the message, the default `Camera1` directory is `/[INTERNEL_STORAGE]/DCIM/Camera1/`. If the directory doesn't exist. Please create it by yourself.

> Attention: `Camera1` in the private directory only works for single app.

4. Open the camera in target app. There will be a toast message showing the resolution (宽width: , 高height:) . And you need to adjust the replacing video's resolution to make them same. Name it as `virtual.mp4`, put it under `Camera1` directory.

5. If there is a toast message when you take photos in app ("发现拍照")，it shows the photo's resolution. You need to prepare a photo which has the same resolution. Name it as `1000.bmp` . Put it under `Camera1` directory. (it support other image format renamed to bmp ). If there isn't a toast message , `1000.bmp` will have nothing to do with replacing capture.

6. If you need to play video's sound, create `./DCIM/Camera1/Camera1/virtual.mp4` under `Camera1` directory. (Global real-time effective)

7. If you need to turn off the module temporarily, create `./DCIM/Camera1/Camera1/virtual.mp4` under `Camera1` directory. (Global real-time effective)

8. If you find toast messages annoying, you can create a `no_toast.jpg` file in the `/[INTERNEL_STORAGE]/DCIM/Camera1/` directory. (Global real-time effective)

9. The directory redirection message is displayed only once by default. If you miss the toast message of directory redirection, you can create a `force_show.jpg` file in the `/[INTERNEL_STORAGE]/DCIM/Camera1/` directory to override the default setting. (Global real-time effective)

10. If you need to allocate videos for each application, you can create `private_dir.jpg` in the `/[INTERNEL_STORAGE]/DCIM/Camera1/` directory to enforce apps use private directory. (Global real-time effective)

> Note: the configuration of 6 ~ 10 are in the application. You can quickly configure them in the application or create files manually.

## FAQ

Q1. The problems of front camera?  
A1. In most cases , the video for replacing front camera need to be flipped horizontally and rotated right 90 degrees. The video's resolution **after being processed** need to same with that in toast message.  But in some came, it doesn't need to make adjustment, so you need to judge it according to situation.

Q2. Black screen ? Open camera fail ?  
A2. Till now ,there are a few apps that can't be hooked, especially the system camera. Or it caused by wrong `Camera1` directory（Whether two levels of Camera1 directory were created, like `./DCIM/Camera1/Camera1/virtual.mp4`, only one level is needed）.

Q3. Blurred screen?  
A3. The resolution of video is wrong.

Q4. Distorted picture?  
A4. Please use the video editing software to modify the original video to match the screen.

Q5. `disable.jpg` invalid?  
A5. If the application version `<=4.0`, then the control files in the `[INTERNEL_STORAGE]/DCIM/Camera1` directory will take effect for the applications that **have access to storage permissions**, and for the rest of the applications without permission, control files should be created in the **private directory**  
If the app version `>=4.1`, it should be created in `[INTERNEL_STORAGE]/DCIM/Camera1` regardless of whether the target app has permissions.

## Question report:

raise it in issues directly. If it is a bug, please attach with Xposed **modules** log.

## Credit

Provide hook method: https://github.com/wangwei1237/CameraHook

H.264 hardware decode： https://github.com/zhantong/Android-VideoToImages

JPEG-YUV convert： https://blog.csdn.net/jacke121/article/details/73888732  
