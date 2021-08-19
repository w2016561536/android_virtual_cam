# android_virtual_cam
xposed安卓虚拟摄像头

已加入Camera2支持，抖音测试通过。
软件对TextureView预览信息替换的视频是 /sdcard/DCIM/Camera/virtual.mp4
软件对onPreviewFrame预览信息替换的**照片**是 /sdcard/DCIM/Camera/bmp/***.bmp
"***.bmp" 是bmp图片，文件命名的规则为：从100.bmp开始，按帧排序依次为101.bmp，102.bmp……110.bmp,111.bmp……，最少有65张图片，最大不超过99张（超过了的话文件名会多一位），可以使用Premiere将视频转化为BMP。

## > 如何获得分辨率？？(仅onPreviewFrame需要，其它系统自动处理)
在目标应用中打开摄像头，若创建回调则即可在Xposed log得到分辨率。

## 请勿用于非法用途，任何法律问题与作者无关。
