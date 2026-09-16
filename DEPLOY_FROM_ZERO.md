# Realtek v3 从零部署

1. 新建空 GitHub 仓库，例如 `realtek-v3`。
2. 解压源码 ZIP。
3. 上传解压后文件夹里面的内容，不要多套一层目录。
4. 仓库首页应直接看到：
   - `.github`
   - `app`
   - `build.gradle.kts`
   - `gradle.properties`
   - `settings.gradle.kts`
5. 打开 Actions。
6. 左侧选择 `Build Realtek APK`。
7. 点击 `Run workflow`。
8. 所有步骤变绿后，在 Artifacts 下载 `Realtek-v7-APK`。
9. 解压得到 `app-debug.apk`。
10. 安装后首次启动设置 4 位密码。
11. 进入 `我 → 设置 → AI 服务` 填写 RunAPI Key。
12. 回消息页使用。

如果 Build APK 红叉，在日志中搜索 `What went wrong`，截取第一次真正错误及上下约 15 行。
