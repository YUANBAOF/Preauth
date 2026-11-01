# Premium Auth (Forge 1.20.1) 正版自动登录 + 盗版注册登录

## 编译（GitHub，不用装环境）
1. 新建一个 GitHub 仓库，把本文件夹里的所有文件（包括隐藏的 `.github` 文件夹）上传。
2. 打开仓库的 **Actions** 页面，等 `build` 跑完（几分钟）。
3. 点进这次运行，页面底部 **Artifacts → premiumauth-jar** 下载，解压得到 jar。
4. 放进服务端 `mods` 文件夹，`server.properties` 设 `online-mode=false`。

## 本地编译
装 JDK 17 和 Gradle 8.1.1，在本目录运行 `gradle build`，jar 在 `build/libs/`。

## 配置 config/premiumauth.properties
- chineseLocales：客户端语言在这个列表里就用中文，默认只有 zh_cn；其他任何语言（含英语）用英语。想让繁体也用中文就写 `zh_cn,zh_tw`
- fallbackLanguage：还拿不到客户端语言时（如登录阶段的断开提示）用的语言，默认 en
- prefix：盗版玩家在聊天/玩家列表里的前缀，只改显示、不改真实名字，默认 `-`，可用 & 当颜色符号
- prefixMode：conflict(名字也是正版账号名时才显示，默认) / all(所有盗版玩家) / off
- loginTimeout、maxFails、markDays

## 命令
- /register 密码 确认密码
- /login 密码
- /premiumauth unmark 名字   （OP，清除“盗版客户端用正版名字”的记录）
