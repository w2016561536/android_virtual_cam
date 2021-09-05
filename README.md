# android_virtual_cam
xposed安卓虚拟摄像头  
## 感谢https://github.com/wangwei1237/CameraHook 提供的HOOK思路！！  
## 求有无极的大佬，希望帮忙测试一下此模块虚拟框架下是否可用，测试后希望在issue中反馈一下，谢谢！！！  

已加入Camera2支持，抖音测试通过，需要**不静音**的可以在no-silent的分支/app/release/app-release.apk下载（no-silent更新很不及时（也可能是不更新了））。（链接全部放下面了）  
### github release里全是静音的。  

## 具体的使用方法(English version is below)：  
1、安装xposed框架（xposed，edxposed，lsposed等均可，不确定虚拟框架能否使用，已经确定VMOS可用，应用转生不可用）  
2、安装模块，启用模块，lsposed等包含定义域的框架需要选勾目标app，但无需选勾系统框架。  
3、将需要替换的视频命名为virtual.mp4，放在/sdcard/DCIM/Camera1/目录下。（前置摄像头需要水平翻转后右旋90°保存，拦截onPreviewFrame需要匹配分辨率）  
4、若需要拦截拍照事件，请在/sdcard/DCIM/Camera1/目录下放置 1000.bmp 用于替换，（前置摄像头需要水平翻转后右旋90°保存，需要匹配分辨率）  
5、**在系统设置中授予目标应用访问存储的权限。**   
6、强制结束目标应用/重启手机。  

## Camera2接口HOOK有问题，需要停用？  
在/sdcard/DCIM/Camera1/下创建disable.jpg，以停用此项HOOK  

## 如何获得分辨率？？(仅拦截onPreviewFrame和拍照需要)
在目标应用中打开摄像头，可在弹出的toast消息里看见。  

## Detailed usage :  
1. Install Xposed framework (Xposed, Lsposed, Edxposed are supported, I'm not sure whether virtual Xposed framework works).  
2. Install this moudle. enable it in Xposed. Framework which has a scope list need to choose target app, but needn't to choose system framework.  
3. Create virtual.mp4 and put it under /sdcard/DCIM/Camera1/ ,(if use front camera ,image should be Flip horizontal and right rotation 90 degrees, if you want to hook onPreviewFrame ,the resolution should be matched)  
4. If you wants to hook image capture event, you should create 1000.bmp under /sdcard/DCIM/Camera1/ for replace. (if use front camera ,image should be Flip horizontal and right rotation 90 degrees, the resolution should be matched)  
5. **authorize the target app to access local storage in system.**  
6. Reboot your phone or shutdown target app.  

## bugs with camera2 api, need to disable it?  
create disable.jpg under /sdcard/DCIM/Camera1/ to disable this method hook.  

## how to get resolution ??(only hook onPreviewFrame and image capture need it)?  
open camera in target app, and you can find resolution in toast message.  

## release无法下载/gitee下载(gitee与github作者同id，同仓库名)？？  
在/app/release/app-release.apk，下载前请注意分支。  
静音（主分支）：GitHub： https://github.com/w2016561536/android_virtual_cam/blob/master/app/release/app-release.apk  
gitee（中国大陆建议此点）： https://gitee.com/w2016561536/android_virtual_cam/blob/master/app/release/app-release.apk  
——————————  
不静音（no-silent分支）：GitHub： https://github.com/w2016561536/android_virtual_cam/blob/no-silent/app/release/app-release.apk   
gitee（中国大陆建议此点）： https://gitee.com/w2016561536/android_virtual_cam/blob/no-silent/app/release/app-release.apk  

## 如果此应用被针对了，解决措施？？
1、使用"mt管理器/np管理器"进行"修改包名/制作共存"，并记住修改好的包名。  
2、编辑安装包内assets中的xposed_init内容，改为“修改后包名+.HookMain”

# 请勿用于非法用途，任何法律问题与作者无关。  
# DO NOT USE FOR ANY ILLEAGLE INTENTION!!YOU NEED TO TAKE ALL RESPONSIBILITY AND CONSEQUENCE!!"  
