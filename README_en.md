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

6. If you need to play video's sound, create `no-silent.jpg` under `Camera1` directory.

7. If you need to turn off the module temporarily, create `disable.jpg` under `Camera1` directory.

## FAQ

Q1. The problems of front camera?  
A1. In most cases , the video for replacing front camera need to be flipped horizontally and rotated right 90 degrees. The video's resolution **after being processed** need to same with that in toast message.  But in some came, it doesn't need to make adjustment, so you need to judge it according to situation.

Q2. Black screen ? Open camera fail ?  
A2. Till now ,there are a few apps that can't be hooked, especially the system camera. Or it caused by wrong `Camera1` directory（Whether two levels of Camera1 directory were created, like `./DCIM/Camera1/Camera1/virtual.mp4`, only one level is needed）.

Q3. Blurred screen?  
A3. The resolution of video is wrong.

## Question report:

raise it in issues directly. If it is a bug, please attach with Xposed **modules** log.

## Credit

Provide hook method: https://github.com/wangwei1237/CameraHook

H.264 hardware decode： https://github.com/zhantong/Android-VideoToImages

JPEG-YUV convert： https://blog.csdn.net/jacke121/article/details/73888732  
