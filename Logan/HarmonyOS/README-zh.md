# Logan HarmonyOS SDK

HarmonyOS SDK 复用 Logan 的 C 核心，日志文件与 Android/iOS SDK 使用相同的压缩、AES-128-CBC 加密和写入协议，现有 Logan Server 可直接解析。

## 设计

- ArkTS 调用只负责参数校验、日期轮转、文件保留和上传文件准备。
- N-API 层使用有界生产者/消费者队列，日志写入不会在 ArkUI 线程执行。
- 所有 clogan_init/open/write/flush 调用都在同一个原生线程串行执行。
- flush() 是顺序屏障：返回时，此前进入队列的日志都已提交给 C 核心。
- 当剩余空间低于阈值、单日日志超过上限或队列已满时停止接收新日志，避免日志系统反向影响业务。

## 环境要求

- DevEco Studio 5.0 或更高版本
- HarmonyOS API 12 或更高版本
- Native C++ 工具链

SDK 默认构建 arm64-v8a 和 x86_64。如需其他 ABI，请修改 build-profile.json5 中的 abiFilters。

## 接入

将 Logan/HarmonyOS 作为 HAR 模块加入工程，并在应用模块的 oh-package.json5 中声明本地依赖：

~~~json5
{
  "dependencies": {
    "@logan/harmonyos": "file:../../../Logan/HarmonyOS"
  }
}
~~~

在 UIAbility.onCreate 中初始化。Key 和 IV 必须分别是 16 个字节，且必须与服务端解密配置一致：

~~~typescript
import { Logan } from '@logan/harmonyos';

Logan.init({
  context: this.context,
  encryptKey: new Uint8Array([48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53]),
  encryptIV: new Uint8Array([48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53]),
  maxFileSize: 10 * 1024 * 1024,
  maxQueueSize: 500,
  retentionDays: 7,
  minFreeSpace: 50 * 1024 * 1024,
  debug: false
});
~~~

写入和刷盘：

~~~typescript
Logan.write('request started', 1);
Logan.w('compatible short API', 2);
Logan.flush();
~~~

## 获取和上传日志

SDK 不绑定业务网络库。prepareUpload() 会先执行顺序刷盘，再为指定日期生成稳定副本，业务上传完成后应删除副本：

~~~typescript
const path: string | undefined = Logan.prepareUpload('2026-09-01');
if (path !== undefined) {
  // 使用业务网络层上传 path。
  // 推荐沿用 Logan Server 的 fileDate、appId、unionId、deviceId 等约定，
  // 并设置 platform=LOGAN_PLATFORM_HARMONY_OS（值为 3）。
  Logan.deletePreparedUpload(path);
}
~~~

查看文件和队列状态：

~~~typescript
const files = Logan.getAllFilesInfo();
const state = Logan.getNativeState();
console.info('pending=' + state.pending + ', dropped=' + state.dropped);
~~~

clearAllLogs() 会刷盘、删除全部日期日志并重新打开当天文件，不会留下指向已删除文件的原生句柄。

## 示例

用 DevEco Studio 打开 Example/Logan-HarmonyOS，或执行：

~~~powershell
ohpm install --all
hvigorw --mode module -p product=default assembleHap
~~~

示例包含单条/批量写入、文件列表、上传副本和原生队列状态展示。
