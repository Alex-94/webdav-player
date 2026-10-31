# WebDAV 播放器（Android TV 极简版）

一个极简的局域网 WebDAV 视频播放器：只显示视频列表，点击即用电视自带的播放器播放。
零第三方依赖（Kotlin + 系统自带网络/XML 库），APK 体积约 1MB。

默认服务器已预填 `http://192.168.31.61:8296`（小柚投屏 WebDAV），安装后开箱即用。

## 功能

- 浏览 WebDAV 目录（文件夹蓝色，视频文件白色并显示大小）
- 点击视频 → 唤起系统默认播放器播放（不内置解码内核）
- 返回键回上级目录，"主页"按钮一键回根目录
- 记住上次浏览的目录
- "设置"按钮可改服务器地址 / 起始路径 / 账号密码

## 如何获得 APK（云端打包）

本项目配置了 GitHub Actions，推送代码后自动打包。

1. 登录 GitHub，新建一个仓库（Public 或 Private 都可以，例如 `webdav-player`）
2. 把本目录所有文件（包括隐藏的 `.github` 文件夹）上传到仓库
   - 网页方式：仓库页 → `Add file` → `Upload files`，把整个文件夹内容拖进去
   - 或命令行：
     ```bash
     cd WebDavPlayer
     git init
     git add .
     git commit -m "init"
     git branch -M main
     git remote add origin https://github.com/<你的用户名>/webdav-player.git
     git push -u origin main
     ```
3. 打开仓库的 `Actions` 标签页，等待 "Build APK" 工作流跑完（约 3~5 分钟）
4. 进入该次运行详情页，在底部 `Artifacts` 区域下载 `WebDavPlayer-apk`，解压得到 `app-debug.apk`

## 安装到电视

1. 把 `app-debug.apk` 拷到 U 盘，插到电视/盒子上，用系统文件管理器安装
   （首次会提示"允许安装未知来源应用"）
2. 在电视应用列表中打开"WebDAV播放器"

## 使用

- 打开后自动列出根目录，遥控器上下选择、OK 键进入/播放
- 文件夹按蓝色显示，点击进入；点视频文件会调用电视默认播放器
- 遥控器返回键 = 回上级目录；在根目录按返回 = 退出
- 如果电视默认播放器打不开某个格式（如 .m2ts），可以在电视上再装一个 MX Player 等播放器，系统会自动询问用哪个打开

## 技术说明

- minSdk 21 / targetSdk 31，Android 5.0 ~ 13+ 均可安装
- WebDAV：`PROPFIND Depth:1` + Basic 认证（留空则不发送）
- 播放：`Intent ACTION_VIEW (video/*)`，链接如有账号密码会以 Basic 凭证形式嵌入 URL
