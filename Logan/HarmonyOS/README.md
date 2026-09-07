# Logan for HarmonyOS

This HAR reuses Logan's native C core through N-API, preserving the Android/iOS gzip, AES-128-CBC, and file protocol. It adds a bounded native worker queue, daily rotation, retention, free-space protection, stable upload copies, and an ArkTS API.

See [README-zh.md](./README-zh.md) for integration and API documentation. A buildable application is available in Example/Logan-HarmonyOS.
