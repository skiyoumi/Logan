import { Logan, LoganFileInfo, LoganNativeState } from '@logan/harmonyos';

@Entry
@Component
struct Index {
  @State output: string = 'Logan is ready';
  private sequence: number = 0;

  build() {
    Column({ space: 16 }) {
      Text('Logan HarmonyOS')
        .fontSize(28)
        .fontWeight(FontWeight.Bold)

      Button('Write one log')
        .onClick(() => {
          this.sequence += 1;
          const accepted: boolean = Logan.write(`HarmonyOS log #${this.sequence}`, 1);
          this.output = accepted ? 'Log accepted by native queue' : 'Log rejected';
        })

      Button('Write 100 logs')
        .onClick(() => {
          let accepted: number = 0;
          for (let index: number = 0; index < 100; index += 1) {
            if (Logan.write(`Batch log ${index}`, 2)) {
              accepted += 1;
            }
          }
          this.output = `Accepted ${accepted}/100 logs`;
        })

      Button('Flush and list files')
        .onClick(() => {
          const files: Array<LoganFileInfo> = Logan.getAllFilesInfo();
          this.output = files.length === 0
            ? 'No log files'
            : files.map((item: LoganFileInfo) => `${item.date}: ${item.size} bytes`).join('\n');
        })

      Button('Prepare today for upload')
        .onClick(() => {
          const path: string | undefined = Logan.prepareUpload(Logan.today());
          this.output = path ?? 'No log file for today';
        })

      Button('Show native queue state')
        .onClick(() => {
          const state: LoganNativeState = Logan.getNativeState();
          this.output = `pending=${state.pending}, dropped=${state.dropped}, status=${state.lastStatus}`;
        })

      Text(this.output)
        .fontSize(14)
        .width('100%')
        .padding(12)
        .backgroundColor('#F5F5F5')
    }
    .width('100%')
    .height('100%')
    .padding(24)
    .justifyContent(FlexAlign.Center)
  }
}
