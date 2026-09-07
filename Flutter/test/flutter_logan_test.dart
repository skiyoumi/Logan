import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_logan/flutter_logan.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const MethodChannel channel = MethodChannel('flutter_logan');
  final List<MethodCall> calls = <MethodCall>[];

  setUp(() {
    calls.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (MethodCall methodCall) async {
      calls.add(methodCall);
      switch (methodCall.method) {
        case 'init':
        case 'upload':
          return true;
        case 'getUploadPath':
          return '/tmp/2026-09-01.logan';
        default:
          return null;
      }
    });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test('forwards the common Logan API over flutter_logan', () async {
    expect(
      await FlutterLogan.init(
          '0123456789012345', '0123456789012345', 10 * 1024 * 1024),
      isTrue,
    );
    await FlutterLogan.log(10, 'hello harmony');
    await FlutterLogan.flush();
    expect(await FlutterLogan.getUploadPath('2026-09-01'),
        '/tmp/2026-09-01.logan');
    expect(
      await FlutterLogan.upload('/logan/upload.json', '2026-09-01',
          'app', 'union', 'device'),
      isTrue,
    );
    await FlutterLogan.cleanAllLogs();

    expect(calls.map((MethodCall call) => call.method), <String>[
      'init',
      'log',
      'flush',
      'getUploadPath',
      'upload',
      'cleanAllLogs',
    ]);
    expect(calls[0].arguments['aesKey'], '0123456789012345');
    expect(calls[4].arguments['deviceId'], 'device');
  });
}
