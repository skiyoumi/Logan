import common from '@ohos.app.ability.common';
import fs from '@ohos.file.fs';
import http from '@ohos.net.http';
import {
  AbilityAware,
  AbilityPluginBinding,
  FlutterPlugin,
  FlutterPluginBinding,
  MethodCall,
  MethodCallHandler,
  MethodChannel,
  MethodResult
} from '@ohos/flutter_ohos';
import { Logan, LOGAN_PLATFORM_HARMONY_OS } from '@logan/harmonyos';

const CHANNEL_NAME: string = 'flutter_logan';
const ERROR_CODE: string = 'flutter_logan';

interface UploadResponse {
  success?: boolean;
  code?: number;
}

export default class FlutterLoganPlugin implements FlutterPlugin, AbilityAware, MethodCallHandler {
  private channel: MethodChannel | null = null;
  private context: common.UIAbilityContext | null = null;

  getUniqueClassName(): string {
    return 'FlutterLoganPlugin';
  }

  onAttachedToEngine(binding: FlutterPluginBinding): void {
    this.channel = new MethodChannel(binding.getBinaryMessenger(), CHANNEL_NAME);
    this.channel.setMethodCallHandler(this);
  }

  onDetachedFromEngine(binding: FlutterPluginBinding): void {
    this.channel?.setMethodCallHandler(null);
    this.channel = null;
  }

  onAttachedToAbility(binding: AbilityPluginBinding): void {
    this.context = binding.getAbility().context;
  }

  onDetachedFromAbility(): void {
    this.context = null;
  }

  onMethodCall(call: MethodCall, result: MethodResult): void {
    try {
      switch (call.method) {
        case 'init':
          this.init(call, result);
          break;
        case 'log':
          Logan.write(String(call.argument('log') ?? ''), Number(call.argument('type')));
          result.success(null);
          break;
        case 'flush':
          Logan.flush();
          result.success(null);
          break;
        case 'getUploadPath':
          result.success(Logan.prepareUpload(String(call.argument('date') ?? '')) ?? '');
          break;
        case 'upload':
          this.upload(call, result);
          break;
        case 'cleanAllLogs':
          Logan.clearAllLogs();
          result.success(null);
          break;
        default:
          result.notImplemented();
          break;
      }
    } catch (error) {
      result.error(ERROR_CODE, this.errorMessage(error), null);
    }
  }

  private init(call: MethodCall, result: MethodResult): void {
    if (this.context === null) {
      result.error(ERROR_CODE, 'FlutterLoganPlugin is not attached to a UIAbility', null);
      return;
    }
    const aesKey: Uint8Array = this.encodeAesPart(String(call.argument('aesKey') ?? ''), 'AES key');
    const aesIv: Uint8Array = this.encodeAesPart(String(call.argument('aesIv') ?? ''), 'AES IV');
    const maxFileLen: number = Number(call.argument('maxFileLen'));
    if (!Number.isInteger(maxFileLen) || maxFileLen <= 0) {
      throw new Error('maxFileLen must be a positive integer');
    }
    Logan.init({
      context: this.context,
      encryptKey: aesKey,
      encryptIV: aesIv,
      maxFileSize: maxFileLen
    });
    result.success(true);
  }

  private upload(call: MethodCall, result: MethodResult): void {
    const serverUrl: string = String(call.argument('serverUrl') ?? '');
    const date: string = String(call.argument('date') ?? '');
    const appId: string = String(call.argument('appId') ?? '');
    const appVersion: string = String(call.argument('appVersion') ?? '');
    const unionId: string = String(call.argument('unionId') ?? '');
    const deviceId: string = String(call.argument('deviceId') ?? '');
    if (serverUrl.length === 0 || date.length === 0) {
      result.error(ERROR_CODE, 'upload_validate: serverUrl and date are required', null);
      return;
    }
    this.uploadPreparedFile(serverUrl, date, appId, unionId, deviceId, appVersion)
      .then((success: boolean): void => {
        result.success(success);
      })
      .catch((error: Error): void => {
        result.error(ERROR_CODE, this.errorMessage(error), null);
      });
  }

  private async uploadPreparedFile(serverUrl: string, date: string, appId: string,
    unionId: string, deviceId: string, appVersion: string): Promise<boolean> {
    const path: string | undefined = Logan.prepareUpload(date);
    if (path === undefined) {
      throw new Error('upload_prepare: no non-empty log file or unable to copy it for date=' + date);
    }
    const request: http.HttpRequest = http.createHttp();
    try {
      const body: ArrayBuffer = this.readFile(path);
      const response: http.HttpResponse = await request.request(serverUrl, {
        method: http.RequestMethod.POST,
        header: {
          'Content-Type': 'binary/octet-stream',
          'client': 'harmonyos',
          'fileDate': date,
          'appId': appId,
          'appVersion': appVersion,
          'unionId': unionId,
          'deviceId': deviceId,
          'platform': LOGAN_PLATFORM_HARMONY_OS.toString()
        },
        extraData: body,
        expectDataType: http.HttpDataType.STRING,
        connectTimeout: 15000,
        readTimeout: 15000
      });
      const responseText: string = typeof response.result === 'string' ? response.result : '<non-string response>';
      const diagnostic: string = `HTTP=${response.responseCode}, bytes=${body.byteLength}, response=${responseText.substring(0, 1024)}`;
      if (response.responseCode < 200 || response.responseCode >= 300) {
        throw new Error(`upload_http: ${diagnostic}`);
      }
      let parsed: UploadResponse;
      try {
        parsed = JSON.parse(responseText) as UploadResponse;
      } catch (_) {
        throw new Error(`upload_response: invalid JSON, ${diagnostic}`);
      }
      if (parsed === null || (parsed.success !== true && parsed.code !== 200)) {
        throw new Error(`upload_server: ${diagnostic}`);
      }
      return true;
    } finally {
      request.destroy();
      Logan.deletePreparedUpload(path);
    }
  }

  private readFile(path: string): ArrayBuffer {
    const size: number = fs.statSync(path).size;
    const buffer: ArrayBuffer = new ArrayBuffer(size);
    const file: fs.File = fs.openSync(path, fs.OpenMode.READ_ONLY);
    try {
      const bytesRead: number = fs.readSync(file.fd, buffer);
      if (bytesRead !== size) {
        throw new Error(`Unable to read the complete Logan upload file: ${bytesRead}/${size}`);
      }
      return buffer;
    } finally {
      fs.closeSync(file);
    }
  }

  private encodeAesPart(value: string, name: string): Uint8Array {
    if (value.length !== 16) {
      throw new Error(`${name} must contain exactly 16 ASCII characters`);
    }
    const bytes: Uint8Array = new Uint8Array(16);
    for (let index: number = 0; index < value.length; index++) {
      const code: number = value.charCodeAt(index);
      if (code > 0x7f) {
        throw new Error(`${name} must contain only ASCII characters`);
      }
      bytes[index] = code;
    }
    return bytes;
  }

  private errorMessage(error: Object): string {
    return error instanceof Error ? error.message : String(error);
  }
}
