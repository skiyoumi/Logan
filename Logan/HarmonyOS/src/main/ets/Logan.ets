import common from '@ohos.app.ability.common';
import fs from '@ohos.file.fs';
import statfs from '@ohos.file.statvfs';
import loganNative, { NativeState } from 'liblogan.so';

const INIT_SUCCESS_MMAP: number = -1010;
const INIT_SUCCESS_MEMORY: number = -1020;
const OPEN_SUCCESS: number = -2010;
const DATE_PATTERN: RegExp = /^\d{4}-\d{2}-\d{2}$/;
const DAY_MILLISECONDS: number = 24 * 60 * 60 * 1000;

export const LOGAN_PLATFORM_HARMONY_OS: number = 3;
export type LoganStatusListener = (command: string, status: number) => void;

export interface LoganConfig {
  context: common.Context;
  encryptKey: Uint8Array;
  encryptIV: Uint8Array;
  cachePath?: string;
  logPath?: string;
  maxFileSize?: number;
  maxQueueSize?: number;
  retentionDays?: number;
  minFreeSpace?: number;
  debug?: boolean;
  statusListener?: LoganStatusListener;
}

export interface LoganFileInfo {
  date: string;
  path: string;
  size: number;
}

export interface LoganNativeState {
  initialized: boolean;
  pending: number;
  dropped: number;
  lastStatus: number;
  currentDate: string;
}

export class Logan {
  private static initialized: boolean = false;
  private static cachePath: string = '';
  private static logPath: string = '';
  private static retentionDays: number = 7;
  private static minFreeSpace: number = 50 * 1024 * 1024;
  private static lastSpaceCheck: number = 0;
  private static canWrite: boolean = true;
  private static statusListener?: LoganStatusListener;

  static init(config: LoganConfig): void {
    if (Logan.initialized) {
      return;
    }
    Logan.validateConfig(config);
    Logan.cachePath = config.cachePath ?? `${config.context.cacheDir}/logan`;
    Logan.logPath = config.logPath ?? `${config.context.filesDir}/logan`;
    Logan.retentionDays = config.retentionDays ?? 7;
    Logan.minFreeSpace = config.minFreeSpace ?? 50 * 1024 * 1024;
    Logan.statusListener = config.statusListener;

    Logan.ensureDirectory(Logan.cachePath);
    Logan.ensureDirectory(Logan.logPath);
    Logan.deleteExpiredFiles();

    const status: number = loganNative.init(
      Logan.cachePath,
      Logan.logPath,
      config.maxFileSize ?? 10 * 1024 * 1024,
      config.encryptKey,
      config.encryptIV,
      config.maxQueueSize ?? 500,
      config.debug ?? false
    );
    Logan.notifyStatus('clogan_init', status);
    if (status !== INIT_SUCCESS_MMAP && status !== INIT_SUCCESS_MEMORY) {
      throw new Error(`Logan native initialization failed: ${status}`);
    }
    Logan.initialized = true;
  }

  static write(log: string, type: number): boolean {
    Logan.assertInitialized();
    if (log.length === 0) {
      return false;
    }
    if (!Number.isInteger(type)) {
      throw new Error('Logan log type must be an integer');
    }
    if (!Logan.hasFreeSpace()) {
      Logan.notifyStatus('disk_space', 0);
      return false;
    }
    const accepted: boolean = loganNative.write(
      Logan.today(),
      type,
      log,
      Date.now(),
      'ArkTS',
      0,
      true
    );
    if (!accepted) {
      Logan.notifyStatus('queue_full', -1);
    }
    return accepted;
  }

  static w(log: string, type: number): boolean {
    return Logan.write(log, type);
  }

  static flush(): number {
    Logan.assertInitialized();
    const status: number = loganNative.flush();
    Logan.notifyStatus('clogan_flush', status);
    return status;
  }

  static f(): number {
    return Logan.flush();
  }

  static setDebug(debug: boolean): void {
    Logan.assertInitialized();
    loganNative.setDebug(debug);
  }

  static getAllFilesInfo(): Array<LoganFileInfo> {
    Logan.assertInitialized();
    Logan.flush();
    const result: Array<LoganFileInfo> = [];
    const names: Array<string> = Logan.listFiles(Logan.logPath);
    names.forEach((name: string) => {
      if (!DATE_PATTERN.test(name)) {
        return;
      }
      const path: string = `${Logan.logPath}/${name}`;
      try {
        const stat: fs.Stat = fs.statSync(path);
        if (stat.isFile()) {
          result.push({ date: name, path: path, size: stat.size });
        }
      } catch (_) {
      }
    });
    result.sort((left: LoganFileInfo, right: LoganFileInfo) => left.date.localeCompare(right.date));
    return result;
  }

  static getFilePath(date: string): string | undefined {
    Logan.assertInitialized();
    Logan.validateDate(date);
    if (date === Logan.today()) {
      Logan.flush();
    }
    const path: string = `${Logan.logPath}/${date}`;
    try {
      const stat: fs.Stat = fs.statSync(path);
      return stat.isFile() && stat.size > 0 ? path : undefined;
    } catch (_) {
      return undefined;
    }
  }

  static prepareUpload(date: string): string | undefined {
    const source: string | undefined = Logan.getFilePath(date);
    if (source === undefined) {
      return undefined;
    }
    const uploadDirectory: string = `${Logan.cachePath}/upload`;
    Logan.ensureDirectory(uploadDirectory);
    const destination: string = `${uploadDirectory}/${date}-${Date.now()}.logan`;
    try {
      fs.copyFileSync(source, destination, 0);
      return destination;
    } catch (_) {
      Logan.notifyStatus('copy_upload_file', -1);
      return undefined;
    }
  }

  static deletePreparedUpload(path: string): boolean {
    Logan.assertInitialized();
    const uploadDirectory: string = `${Logan.cachePath}/upload/`;
    if (!path.startsWith(uploadDirectory) || path.substring(uploadDirectory.length).includes('/')) {
      throw new Error('The path is not a Logan prepared-upload file');
    }
    try {
      fs.unlinkSync(path);
      return true;
    } catch (_) {
      return false;
    }
  }

  static clearAllLogs(): void {
    Logan.assertInitialized();
    Logan.flush();
    const names: Array<string> = Logan.listFiles(Logan.logPath);
    names.forEach((name: string) => {
      if (DATE_PATTERN.test(name)) {
        try {
          fs.unlinkSync(`${Logan.logPath}/${name}`);
        } catch (_) {
        }
      }
    });
    const status: number = loganNative.reopen(Logan.today());
    Logan.notifyStatus('clogan_open', status);
    if (status !== OPEN_SUCCESS) {
      throw new Error(`Logan failed to reopen current log: ${status}`);
    }
  }

  static getNativeState(): LoganNativeState {
    const state: NativeState = loganNative.getState();
    return {
      initialized: state.initialized,
      pending: state.pending,
      dropped: state.dropped,
      lastStatus: state.lastStatus,
      currentDate: state.currentDate
    };
  }

  static today(): string {
    return Logan.formatDate(new Date());
  }

  private static validateConfig(config: LoganConfig): void {
    if (config.context === undefined || config.context === null) {
      throw new Error('Logan context is required');
    }
    if (config.encryptKey.byteLength !== 16 || config.encryptIV.byteLength !== 16) {
      throw new Error('Logan AES key and IV must each contain exactly 16 bytes');
    }
    const maxFileSize: number = config.maxFileSize ?? 10 * 1024 * 1024;
    if (!Number.isInteger(maxFileSize) || maxFileSize <= 0 || maxFileSize > 2147483647) {
      throw new Error('Logan maxFileSize must be a positive 32-bit integer');
    }
    const maxQueueSize: number = config.maxQueueSize ?? 500;
    if (!Number.isInteger(maxQueueSize) || maxQueueSize <= 0) {
      throw new Error('Logan maxQueueSize must be a positive integer');
    }
    const retentionDays: number = config.retentionDays ?? 7;
    if (!Number.isInteger(retentionDays) || retentionDays <= 0) {
      throw new Error('Logan retentionDays must be a positive integer');
    }
  }

  private static assertInitialized(): void {
    if (!Logan.initialized) {
      throw new Error('Please initialize Logan first');
    }
  }

  private static ensureDirectory(path: string): void {
    try {
      const stat: fs.Stat = fs.statSync(path);
      if (!stat.isDirectory()) {
        throw new Error(`Logan path is not a directory: ${path}`);
      }
    } catch (_) {
      try {
        fs.mkdirSync(path);
      } catch (_) {
        throw new Error(`Unable to create Logan directory: ${path}`);
      }
    }
  }

  private static hasFreeSpace(): boolean {
    const now: number = Date.now();
    if (now - Logan.lastSpaceCheck < 60 * 1000) {
      return Logan.canWrite;
    }
    Logan.lastSpaceCheck = now;
    try {
      Logan.canWrite = statfs.getFreeSizeSync(Logan.logPath) > Logan.minFreeSpace;
    } catch (_) {
      Logan.canWrite = false;
    }
    return Logan.canWrite;
  }

  private static deleteExpiredFiles(): void {
    const cutoff: number = Date.now() - Logan.retentionDays * DAY_MILLISECONDS;
    const names: Array<string> = Logan.listFiles(Logan.logPath);
    names.forEach((name: string) => {
      if (!DATE_PATTERN.test(name)) {
        return;
      }
      const timestamp: number = Logan.parseDate(name);
      if (timestamp > 0 && timestamp < cutoff) {
        try {
          fs.unlinkSync(`${Logan.logPath}/${name}`);
        } catch (_) {
        }
      }
    });
  }

  private static validateDate(date: string): void {
    if (!DATE_PATTERN.test(date) || Logan.parseDate(date) <= 0) {
      throw new Error('Logan date must use yyyy-MM-dd format');
    }
  }

  private static listFiles(path: string): Array<string> {
    try {
      return fs.listFileSync(path);
    } catch (_) {
      Logan.notifyStatus('list_files', -1);
      return [];
    }
  }

  private static parseDate(value: string): number {
    const parts: Array<string> = value.split('-');
    if (parts.length !== 3) {
      return 0;
    }
    const year: number = Number(parts[0]);
    const month: number = Number(parts[1]);
    const day: number = Number(parts[2]);
    const date: Date = new Date(year, month - 1, day);
    return Logan.formatDate(date) === value ? date.getTime() : 0;
  }

  private static formatDate(date: Date): string {
    const year: string = date.getFullYear().toString();
    const month: string = (date.getMonth() + 1).toString().padStart(2, '0');
    const day: string = date.getDate().toString().padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private static notifyStatus(command: string, status: number): void {
    if (Logan.statusListener !== undefined) {
      try {
        Logan.statusListener(command, status);
      } catch (_) {
      }
    }
  }
}
