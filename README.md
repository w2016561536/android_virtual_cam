# android_virtual_cam
xposed安卓虚拟摄像头  
## 感谢https://github.com/wangwei1237/CameraHook 提供的HOOK思路！！  

已加入Camera2支持，抖音测试通过，需要**不静音**的可以在no-silent的分支里./app/release/app-release.apk下载。直达链接：https://github.com/w2016561536/android_virtual_cam/blob/no-silent/app/release/app-release.apk  
### github release里全是静音的。  
## 软件对TextureView预览信息替换的视频是 /sdcard/DCIM/Camera/virtual.mp4  
## 软件对onPreviewFrame预览信息替换的**照片**是 /sdcard/DCIM/Camera/bmp/****.bmp
 **命名规则**："****.bmp" 是bmp图片，文件命名的规则为：从1000.bmp开始，按帧排序依次为1000.bmp，1001.bmp，1002.bmp……，最少有3张图片，最大不超过999张（超过了的话文件名会多一位），可以使用Premiere将视频转化为BMP。  

## 具体的使用方法：
1、安装xposed框架（传统xposed，edxp，lsp等均可，不确定虚拟框架能否使用，已经确定VMOS可用，应用转生不可用）  
2、安装模块，启用模块，lsp等包含定义域的框架需要选勾目标app，但无需选勾系统框架。  
3、对于大多数应用，只需要将替换的视频命名为virtual.mp4，放在/sdcard/DCIM/Camera/目录下。  
4、多余少部分应用（如腾讯会议，和其他应用大部分的二维码扫描），需要使用premiere或其它剪辑软件将视频拆分成BMP格式图片（命名格式见上，PR视频总帧数超过1000帧自动按以上的命名格式命名），要注意的是图片分辨率需要与目标分辨率匹配（获取分辨率方法见下），将这些图片放在/sdcard/DCIM/Camera/bmp/目录下（没有的话自己创建）。  
5、强制结束目标应用/重启手机。  

## > 如何获得分辨率？？(仅onPreviewFrame需要，其它系统自动处理)  
在目标应用中打开摄像头，若创建回调则即可在Xposed log得到分辨率（可查看到字样为：预览回调初始化：宽……高…………）。  

## Camera2接口有问题？？  
是的，目前Camera2接口的HOOK不是所有应用程序都能生效，部分app报错打开相机失败，如果想停用Camera2接口的HOOK，可在/sdcard/DCIM下创建disable.jpg，以停用此项HOOK  

## release无法下载/gitee下载(gitee与github作者同id，同仓库名)？？  
在/app/release/app-release.apk，下载前请注意分支。  

# 请勿用于非法用途，任何法律问题与作者无关。  
