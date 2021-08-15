# android_virtual_cam
xposed安卓虚拟摄像头

软件对TextureView预览信息替换的视频是 /sdcard/DCIM/Camera/virtual.mp4
软件对onPreviewFrame预览信息替换的**照片**是 /sdcard/DCIM/Camera/virtual.jpg
## virtual.jpg 并不是jpg图片，其格式应为YUV420SP，使用jpg后缀是为了避免安卓权限管理影响，可以用 https://github.com/smewise/Camera2 生成，其软件内标注的格式为YUV_420_888,使用图片时请注意分辨率应与目标应用匹配。
## > 如何获得分辨率？？
在目标应用中打开摄像头，若创建回调则即可在Xposed log得到分辨率。

## 请勿用于非法用途，任何法律问题与作者无关。
