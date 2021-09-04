# android_virtual_cam
xposed安卓虚拟摄像头  
## 感谢https://github.com/wangwei1237/CameraHook 提供的HOOK思路！！  
## 求有无极的大佬，希望帮忙测试一下此模块虚拟框架下是否可用，测试后希望在issue中反馈一下，谢谢！！！  

已加入Camera2支持，抖音测试通过，需要**不静音**的可以在no-silent的分支/app/release/app-release.apk下载（no-silent更新很不及时（也可能是不更新了））。（链接全部放下面了）  
### github release里全是静音的。  

## 具体的使用方法（现在全部在Camera1里了，Camera里找东西真麻烦）：  
1、安装xposed框架（传统xposed，edxp，lsposed等均可，不确定虚拟框架能否使用，已经确定VMOS可用，应用转生不可用）    
2、安装模块，启用模块，lsposed等包含定义域的框架需要选勾目标app，但无需选勾系统框架。  
3、将需要替换的视频命名为virtual.mp4，放在/sdcard/DCIM/Camera1/目录下。（前置摄像头需要水平翻转后右旋90°保存，onPreviewFrame需要匹配分辨率）  
4、若需要拦截拍照事件，请在/sdcard/DCIM/Camera1/目录下放置 1000.bmp 用于替换，（前置摄像头需要水平翻转后右旋90°保存，需要匹配分辨率）  
5、**授予目标应用读取本地文件的权限，至少是允许读取媒体文件。**  
6、强制结束目标应用/重启手机。  

## > 如何获得分辨率？？(仅拦截onPreviewFrame和拍照需要，其它系统自动处理)  
在目标应用中打开摄像头，可在弹出的toast消息里看见。  

## Camera2接口有问题？？  
是的，目前Camera2接口的HOOK不是所有应用程序都能生效，部分app报错打开相机失败，如果想停用Camera2接口的HOOK，可在/sdcard/DCIM/Camera1/下创建disable.jpg，以停用此项HOOK  

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
