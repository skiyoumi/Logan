export interface NativeState {
  initialized: boolean;
  pending: number;
  dropped: number;
  lastStatus: number;
  currentDate: string;
}

interface LoganNative {
  init(cachePath: string, logPath: string, maxFileSize: number, encryptKey: Uint8Array,
    encryptIV: Uint8Array, maxQueue: number, debug: boolean): number;
  write(date: string, type: number, log: string, timestamp: number, threadName: string,
    threadId: number, isMainThread: boolean): boolean;
  flush(): number;
  reopen(date: string): number;
  setDebug(debug: boolean): number;
  getState(): NativeState;
}

declare const loganNative: LoganNative;
export default loganNative;
