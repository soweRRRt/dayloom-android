# Physical-device QA record

This file is the durable record for issue #21. Emulator results are useful for finding regressions but are not accepted as physical-device evidence.

| Check | Emulator | Physical device |
|---|---:|---:|
| Cold start | Macrobenchmark configured | Pending device run |
| Primary navigation frame timing | Macrobenchmark configured | Pending device run |
| Large lists and calendar | Automated/UI coverage | Pending device run |
| Reboot and reminders | Scheduler unit coverage | Pending device run |
| Clock and time-zone changes | Scheduler unit coverage | Pending device run |
| Battery restrictions | Permission guidance implemented | Pending device run |
| Vault plaintext in logs/backups | Backup disabled; encrypted repository tests | Pending device audit |

Macrobenchmark replaces the target APK and may clear its app data. Use a dedicated disposable device only; never run it on a working Dayloom installation. For an isolated physical run, set `DAYLOOM_ALLOW_DESTRUCTIVE_BENCHMARK=true`, execute `./gradlew :benchmark:connectedBenchmarkAndroidTest`, note the device model/Android version/date below, attach the benchmark output to issue #21, and replace each pending cell with the measured result.

Device: _pending_  
Android version: _pending_  
Date: _pending_  
Tester: _pending_
