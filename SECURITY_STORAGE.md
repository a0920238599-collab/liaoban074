# Realtek v3 数据保存方式

## 文字
SQLite 数据库在 App 私有目录：
`/data/user/0/com.realtek.chat/databases/realtek_v3.db`

以下内容加密后写入数据库：
- 聊天文本
- 联系人核心人格
- 联系人说话方式
- 长期记忆
- 聊天摘要

加密密钥由 Android Keystore 生成与管理。

## 图片
头像、背景、聊天图片先压缩，再 AES-GCM 加密，保存到：
`/data/user/0/com.realtek.chat/files/secure_media/`

文件扩展名为 `.bin`，不是可直接打开的普通图片。

## 备份
- `android:allowBackup="false"`
- `backup_rules.xml` 排除内部数据
- `data_extraction_rules.xml` 排除云备份与设备迁移

## 卸载
Realtek 不把数据写入共享存储。
卸载应用时 Android 会删除该 applicationId 的 App 私有目录，因此本地数据一起删除。


## v7 社交状态
新增本地表：
- `social_state`
- `ai_topic_history`

其中 mood/current_topic/topic 等文本仍使用 Android Keystore 管理的 AES-GCM 密钥加密后保存。
卸载应用时与其他 App 私有数据一起删除。
