# android_virtual_cam
xposed安卓虚拟摄像头  
## 感谢https://github.com/wangwei1237/CameraHook 提供的HOOK思路！！  
## 求有无极的大佬，希望帮忙测试一下此模块虚拟框架下是否可用，测试后希望在issue中反馈一下，谢谢！！！  

## 具体的使用方法(English version is below)：   
1、安装xposed框架（传统xposed，edxp，lsposed等均可，不确定虚拟框架能否使用，已经确定VMOS可用，应用转生不可用）    
2、安装模块，启用模块，lsposed等包含定义域的框架需要选勾目标app，但无需选勾系统框架。  
3、将需要替换的视频命名为`virtual.mp4`，放在`/sdcard/DCIM/Camera1/`目录下。（前置摄像头需要水平翻转后右旋90°保存，onPreviewFrame需要匹配分辨率）  
4、若需要拦截拍照事件，请在`/sdcard/DCIM/Camera1/`目录下放置 `1000.bmp` 用于替换，（前置摄像头需要水平翻转后右旋90°保存，需要匹配分辨率）  
5、**授予目标应用读取本地文件的权限，至少是允许读取媒体文件。**  
6、强制结束目标应用/重启手机。  

## 如何获得分辨率？？(仅拦截onPreviewFrame和拍照需要，其它系统自动处理)  
在目标应用中打开摄像头，可在弹出的toast消息里看见。  

## Camera2接口有问题？？  
是的，目前Camera2接口的HOOK不是所有应用程序都能生效，部分app报错打开相机失败，如果想停用Camera2接口的HOOK，可在`/sdcard/DCIM/Camera1/`下创建`disable.jpg`，以停用此项HOOK  

## 我不需要静音？？
在`/sdcard/DCIM/Camera1/`下创建`no-silent.jpg`，就不会静音了。

## Detailed usage :
1. Install this moudle. enable it in Xposed. Framework which has a scope list need to choose target app, but needn't to choose system framework.  
2. Create `virtual.mp4` and put it under `/sdcard/DCIM/Camera1/` ,(if use front camera ,image should be Flip horizontal and right rotation 90 degrees, if you want to hook onPreviewFrame ,the resolution should be matched)  
3. If you wants to hook image capture event, you should create `1000.bmp` under `/sdcard/DCIM/Camera1/` for replace. (if use front camera ,image should be Flip horizontal and right rotation 90 degrees, the resolution should be matched)  
4. authorize the target app to access local storage in system.  
5. Reboot your phone or shutdown target app.  
## bugs with camera2 api, need to disable it?
create `disable.jpg` under `/sdcard/DCIM/Camera1/` to disable this method hook.  
## how to get resolution ??(only hook onPreviewFrame and image capture need it)?
open camera in target app, and you can find resolution in toast message.  
## Needn't mute?
Create `no-silent.jpg` under `/sdcard/DCIM/Camera1/`, and it will play sounds.  

## release无法下载/gitee下载(gitee与github作者同id，同仓库名)？？  
在/app/release/app-release.apk，下载前请注意分支。  
GitHub： https://github.com/w2016561536/android_virtual_cam/blob/master/app/release/app-release.apk  
gitee（中国大陆建议此点）： https://gitee.com/w2016561536/android_virtual_cam/blob/master/app/release/app-release.apk  

# 请勿用于非法用途，所有后果自负。  
# DO NOT USE FOR ANY ILLEAGLE INTENTION!!YOU NEED TO TAKE ALL RESPONSIBILITY AND CONSEQUENCE!!"
