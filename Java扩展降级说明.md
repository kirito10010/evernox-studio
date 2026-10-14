# ⚠️ Java 扩展版本提示（请勿升级到 1.57）

> 最后更新：2026-10-14

## 一句话

Comate 里的 Java 扩展 `redhat.java` 已**手动固定为 1.56.0**，并**关闭了自动更新**。
在你看到本文档更新之前，**不要把它升级到 1.57.x**。

---

## 现象（如果又出现，就是被升回 1.57 了）

后端 `evernox-backend` 里大量文件报同一个错：

```
The Java feature 'Flexible Constructor Bodies' is only available with source level 25 and above
```

- 报错位置几乎都在 Lombok 注解行（`@Data` / `@Builder` / `@AllArgsConstructor` / `@RequiredArgsConstructor`）或类声明行；
- **这是 IDE 的幻影错误，不是代码问题**：`mvn clean compile` / `mvn install` 完全通过，项目就是 Java 21。

## 原因

`redhat.java` **1.57.0** 的回归 bug。Java 扩展底层复用 Eclipse 编译器（ECJ），Lombok 靠 hook ECJ 内部实现，1.57 里 ECJ 对 Java 25 新特性（Flexible Constructor Bodies）的改动破坏了 Lombok 的 hook，于是对每个 Lombok 注解类都误报。

- 上游 issue：`redhat-developer/vscode-java#4521`
- 根因 issue：`projectlombok/lombok#4084`
- 确认：1.56.0 / 1.55 正常，1.57.0 复现（Lombok 1.18.30 与 1.18.42 都一样）

## 已经做过的处理

| 项 | 值 |
|---|---|
| 扩展目录 | `C:\Users\kirito\.comate\extensions\` |
| 使用版本 | `redhat.java-1.56.0-win32-x64`（1.57.0 已标记进 `.obsolete`，重启后清除） |
| 注册表 | `...\.comate\extensions\extensions.json` 中 `redhat.java` → 1.56.0 |
| 关闭自动更新 | `C:\Users\kirito\AppData\Roaming\Comate\User\settings.json` → `"extensions.autoUpdate": false` |
| 备份 | 同目录下 `extensions.json.bak`、`settings.json.bak` |

## 你需要注意的

1. **别点升级**。扩展面板（`Ctrl+Shift+X`）里如果 redhat.java 出现 "Update" 按钮，先看版本号，**只有 ≥ 1.58 才升**。
2. 自动更新已被全局关闭，其他扩展也不会自动更新了——想恢复的话，`Ctrl+,` 搜 `auto update`，把 **Extensions: Auto Update** 从 `None` 改回去。

## 什么时候可以升级

满足**任一**即可：

- 扩展面板里 redhat.java 的可用版本 **≥ 1.58**；
- 或下面任一 issue 已关闭 / 明确标注修复版本：
  - https://github.com/redhat-developer/vscode-java/issues/4521
  - https://github.com/projectlombok/lombok/issues/4084
- 官方发布记录：https://github.com/redhat-developer/vscode-java/releases

升级后如果那批报错又出现，说明修复没到位，按下面回滚。

## 升级 / 回滚方法

**升级（确认 ≥ 1.58 后）**：扩展面板 → redhat.java → 齿轮 → Install Specific Version…，选新版本，重载窗口。

**回滚到 1.56.0**：重复当时那套操作即可 ——
1. 从 `C:\Users\kirito\.vscode\extensions\redhat.java-1.56.0-win32-x64` 复制到 `C:\Users\kirito\.comate\extensions\`；
2. 改 `extensions.json` 指向 1.56.0，把旧版本写进 `.obsolete`；
3. 彻底重启 Comate（不是"重载窗口"，要完整退出再开）。

**快速重置**：直接用 `extensions.json.bak` / `settings.json.bak` 覆盖回去，再重启。

## 出现报错时的急救步骤

1. 先确认版本：扩展面板看 redhat.java 是不是被升到 1.57 了；
2. 是的话 → 按上面回滚到 1.56.0 → **彻底重启 Comate**；
3. 回滚后还剩零星报错 → 命令面板执行 `Java: Clean Java Language Server Workspace`。

---

> 备注：本文档只是环境备忘，不属于代码逻辑，可按需决定是否提交到 git 或加进 `.gitignore`。
