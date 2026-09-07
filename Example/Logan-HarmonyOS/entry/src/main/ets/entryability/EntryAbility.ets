import UIAbility from '@ohos.app.ability.UIAbility';
import Want from '@ohos.app.ability.Want';
import window from '@ohos.window';
import { Logan } from '@logan/harmonyos';

export default class EntryAbility extends UIAbility {
  onCreate(want: Want): void {
    Logan.init({
      context: this.context,
      encryptKey: new Uint8Array([48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53]),
      encryptIV: new Uint8Array([48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53]),
      maxFileSize: 10 * 1024 * 1024,
      maxQueueSize: 500,
      retentionDays: 7,
      debug: true,
      statusListener: (command: string, status: number): void => {
        console.info(`Logan ${command}: ${status}`);
      }
    });
    Logan.write('EntryAbility created', 1);
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    windowStage.loadContent('pages/Index').catch((error: Error) => {
      console.error(`Failed to load Logan example page: ${error.message}`);
    });
  }

  onBackground(): void {
    Logan.flush();
  }
}
