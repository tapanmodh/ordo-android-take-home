# Ordo Take-Home

This project explores two areas on Android:

1. **On-device LLM inference** using `llama.cpp` and GGUF models.
2. **Cross-app UI interaction** using a minimal `AccessibilityService`.

Primary test device: **Pixel 6 Pro**.

---

## What surprised me

- **Q8 was not simply “Q4 but larger/slower.”** Qwen3 1.7B Q8_0 used much more RAM and took ~4× longer to load, yet its prefill throughput was substantially higher than Q4_K_M on this device. Quantization-to-latency was clearly not linear.
- **Short benchmarks overstated sustained performance by roughly 2×.** Qwen Q4 started near 60 tok/s prefill / 9 tok/s decode and settled near 30 / 4.5 after repeated generations while battery temperature rose from 38.8°C to 43.9°C.
- **`ModelReady` does not mean fully resident in RAM.** Under memory pressure the process survived while ~1 GiB was swapped out; inference still worked, but the first request after returning was slower.
- **Background generation continued, but much more slowly.** A background-heavy run completed normally at ~2.6 decode tok/s versus ~7–8 tok/s in a cooler foreground run.
- **Battery Saver did not produce an obvious foreground inference penalty** in my matched test. Thermal/sustained state had a much larger effect.
- **Accessibility automation was more stateful than expected.** Visible text nodes were often not clickable, transition events briefly exposed stale hierarchy, and repeated events made idempotence important.

---

## Repository layout

- `app/` — Android application, benchmark UI, benchmark helpers and Accessibility demo
- `llama/` — local Android/JNI wrapper around llama.cpp
- `third_party/llama.cpp/` — pinned upstream llama.cpp git submodule

## Setup / How to reproduce

| Item | Configuration |
|---|---|
| Device | Pixel 6 Pro |
| Android | Android 17 |
| Build | `CP2A.260705.006` |
| Runtime | `llama.cpp` GGUF |
| Backend | CPU, 4 threads |
| Context | 8192 |
| Batch / ubatch | 512 / 512 |
| Output cap | 128 tokens |
| Android minSdk | 26 |
| NDK / CMake | 29.0.13113456 / 3.31.6 |
| llama.cpp | submodule at `third_party/llama.cpp` |
| Tested commit | `4695f001f` |

### Models tested

- [`Qwen3-1.7B-Q4_K_M.gguf`](https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/blob/main/Qwen3-1.7B-Q4_K_M.gguf)
- [`Qwen3-1.7B-Q8_0.gguf`](https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/blob/main/Qwen3-1.7B-Q8_0.gguf)
- [`smollm2-1.7b-instruct-q4_k_m.gguf`](https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF/blob/main/smollm2-1.7b-instruct-q4_k_m.gguf)


The app uses a small local `llama` wrapper module based on `examples/llama.android/lib`; upstream `llama.cpp` is left untouched. The wrapper keeps the original `com.arm.aichat` package because JNI symbols depend on it.

The wrapper dynamically loads native backend libraries, so Android packaging uses:

```kotlin
android {
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}
```

### Build and run

After cloning this repository, initialize the pinned `llama.cpp` submodule:

```bash
git submodule update --init --recursive
```

Then:

1. Open the project in Android Studio.
2. Let Gradle sync and install the configured NDK/CMake versions if required.
3. Build and run the `app` configuration on an **ARM64 Android device**.
4. Copy one of the tested GGUF files to the device.
5. Launch Ordo Take-Home and tap **Select model**.
6. Select the GGUF file and wait until **Model ready** is shown.
7. Tap **Benchmark prompt** to insert the fixed long prompt.
8. Tap **Generate**.

The Generation section reports:

- prompt token count,
- prefill time and throughput,
- first-token decode latency,
- TTFT,
- generated token count,
- decode time and throughput.

Model files are not committed. Selected GGUF is copied into app-private storage and its private path is remembered so a fresh process can reload it.

### Reproduce baseline measurements

For each model configuration:

1. Load the model and confirm **Model ready**.
2. Use the same benchmark prompt.
3. Run one generation with the 128-token output cap.
4. Force-stop/relaunch the app before the next baseline run so the remembered model is auto-loaded.
5. Repeat for **three fresh-process runs** and use the median values.

`Native load` measures `engine.loadModel()` only. `Cold → ready` measures process start to the remembered model becoming ready.

For process-memory snapshots:

```bash
adb shell dumpsys meminfo com.tm.ordotakehome
```

I use **TOTAL PSS** as the primary memory number and RSS/swap as supporting signals.

### Reproduce the sustained / battery benchmark

1. Load `Qwen3-1.7B-Q4_K_M.gguf`.
2. Keep Battery Saver **OFF**.
3. Disconnect the phone from external power.
4. Tap **Run 20 generations**.
5. The app runs the same fixed 747-token prompt 20 times consecutively.
6. Conversation/KV/sampler state is reset between runs while the model remains loaded.
7. Capture battery state before and after with:

```bash
adb shell dumpsys battery
```

The app displays per-run prefill, TTFT, generated-token count and decode throughput so the sustained falloff can be reproduced directly.

### Reproduce the Accessibility demo

1. Tap **Accessibility settings** and enable **Ordo Accessibility Demo**.
2. Return to Ordo Take-Home.
3. Tap **Open Settings & turn Bluetooth on**.
4. The service reads the Settings accessibility hierarchy and navigates toward Bluetooth.
5. If Bluetooth is already ON, it leaves the switch unchanged.

### Model selection

I chose **Qwen3 1.7B** and **SmolLM2 1.7B Instruct** because they are different families at roughly the same parameter size. This avoids a comparison dominated mainly by parameter count.

I also tested Qwen in two quantizations:

| Configuration | Purpose |
|---|---|
| Qwen3 1.7B Q4_K_M | Main baseline |
| Qwen3 1.7B Q8_0 | Same-model quantization comparison |
| SmolLM2 1.7B Instruct Q4_K_M | Different-family comparison at similar size |

This lets me ask both **“what changes when quantization changes?”** and **“what changes when the model family changes?”**

---

# Part 1 — On-device LLM

## Methodology

Baseline results are medians of **three fresh-process runs** per configuration using the same long English prompt. The text produced **745 Qwen tokens** and **767 SmolLM tokens** because the tokenizers differ. Models were allowed to stop naturally at EOS.

The sustained/stress benchmark uses an in-app fixed prompt that currently tokenizes to **747 Qwen tokens**, with a 128-token output cap. Conversation/KV/sampler state is reset between benchmark runs while the model remains loaded.

Native instrumentation records actual model tokens and timings:

- **Prefill throughput** = prompt tokens / prefill time
- **First-token decode** = generation start → first generated token after prefill
- **TTFT** = prefill + first-token decode
- **Decode throughput** = generated tokens / total decode time

Qwen `<think>` tokens count as generated model tokens even if the UI hides them.

## Results

Headline values are medians of the three fresh-process baseline runs. Peak PSS is the highest `dumpsys meminfo` PSS value observed during the corresponding model tests.

| Configuration | Prompt tokens | Native load | Cold → ready | Prefill | TTFT | Decode | Peak PSS |
|---|---:|---:|---:|---:|---:|---:|---:|
| Qwen3 1.7B Q4_K_M | 745 | **2.72 s** | **3.00 s** | 55.78 tok/s | 13.47 s | **7.25 tok/s** | **~3.05 GiB** |
| Qwen3 1.7B Q8_0 | 745 | 10.84 s | 11.15 s | **90.57 tok/s** | **8.36 s** | 6.36 tok/s | ~4.47 GiB |
| SmolLM2 1.7B Q4_K_M | 767 | **2.24 s** | **2.57 s** | 51.38 tok/s | 15.04 s | 6.82 tok/s | ~3.67 GiB |

`Native load` measures `engine.loadModel()` only. `Cold → ready` measures fresh process start to the remembered model becoming ready.

### Q4 vs Q8

| Metric | Q4_K_M | Q8_0 |
|---|---:|---:|
| Native load | **2.72 s** | 10.84 s |
| Cold → ready | **3.00 s** | 11.15 s |
| Prefill | 55.78 tok/s | **90.57 tok/s** |
| Decode | **7.25 tok/s** | 6.36 tok/s |
| Peak PSS | **~3.05 GiB** | ~4.47 GiB |

Q8 used ~1.4 GiB more process memory, took ~4× longer to load, and decoded slightly slower, but prefetched substantially faster. I would not claim a precise cause without kernel profiling; my hypothesis is that Q4/Q8 exercise different optimized CPU paths and that prefill/decode have different compute/memory characteristics.

> **Smaller weights did not translate into proportionally lower latency.**

### Why decode is slower than prefill

Prefill knows all input tokens up front, so many tokens can be processed in batches with high parallelism. Decode is autoregressive: token `N+1` depends on token `N`, limiting token-level parallelism while repeatedly accessing model weights and the growing KV cache.

That is why a model can process tens of prompt tokens per second yet generate only a handful of new tokens per second.

### TTFT and a `<2 s` target

For conversational UX, **TTFT** is the most important initial latency metric. With the long Qwen Q4 prompt, prefill was roughly 13 s while first-token decode was only ~0.1 s. Therefore the main path toward `<2 s` is not “optimize the first decode call”; it is reducing/reusing context and improving prefill.

I would prioritize shorter relevant context, avoiding unnecessary re-prefill, reusable conversation/KV state where appropriate, better prefill kernels/backends, keeping the model warm during active workflows, and possibly a smaller model for latency-critical actions.

## Peak RAM

`adb shell dumpsys meminfo` was sampled during model-ready and generation states. I use **TOTAL PSS** as the primary metric and RSS/swap as supporting signals.

| Configuration | Ready PSS | Peak PSS | Peak RSS |
|---|---:|---:|---:|
| Qwen3 Q4 | ~2.96 GiB | **~3.05 GiB** | ~3.19 GiB |
| SmolLM2 Q4 | ~3.64 GiB | **~3.67 GiB** | ~3.80 GiB |
| Qwen3 Q8 | ~4.38 GiB | **~4.47 GiB** | ~4.60 GiB |

Model file size therefore understates runtime cost; model mappings, context/KV state, native structures and working buffers all contribute. Java heap alone would be misleading.

## Sustained inference, thermals and battery

I ran **20 consecutive Qwen Q4 generations** with the fixed 747-token prompt, 128-token cap and the model kept loaded.

| Metric | Run 1 | Run 20 | Change |
|---|---:|---:|---:|
| Prefill | 60.04 tok/s | 29.73 tok/s | -50.5% |
| Decode | 8.94 tok/s | 4.58 tok/s | -48.8% |
| TTFT | 12.54 s | 25.35 s | +102% |

The workload took **17m 02s**. Battery moved **56% → 45%**, charge counter **2384 → 1984 mAh**, and battery temperature **38.8°C → 43.9°C**.

Performance dropped quickly over the first few runs and then stabilized around **30 tok/s prefill / 4.5 tok/s decode**. This pattern is strongly consistent with thermal/sustained CPU-frequency limits, although it was not a controlled thermal experiment.

**Battery Saver:** three matched runs showed no obvious slowdown. Average ON was 61.24 prefill / 9.14 decode tok/s; OFF was 56.65 / 7.31. I do **not** interpret this as Battery Saver making inference faster—only that sustained thermal state was the much larger effect in this test.

## Memory pressure and backgrounding

With Qwen Q4 loaded, I backgrounded Ordo and opened several heavy apps. The process initially survived at roughly **2.10 GiB PSS / 1.18 GiB RSS / 1.04 GiB Swap PSS** with `oom_score_adj=970`.

The first inference after returning still completed, but at **50.95 prefill tok/s, 14.78 s TTFT, 5.51 decode tok/s**. Afterwards RSS increased and swap decreased as pages became resident again.

Later, after more pressure/backgrounding, the app needed to reload the remembered model. I did not capture the exact kill/recreation boundary.

The practical lifecycle is:

1. **Loaded + resident** → fast
2. **Loaded but paged/swapped** → slower first request
3. **Native/process state reclaimed** → reload from persisted model metadata

Background generation also continued successfully. In the longest background-heavy run it completed 128 tokens at **52.37 prefill tok/s, 14.38 s TTFT, 2.60 decode tok/s**. Foreground throughput therefore cannot be assumed to persist after the app loses foreground priority.

## Shipping conclusions

### Model choice

Of the tested options, I would start with **Qwen3 1.7B Q4_K_M**: much faster load/recovery than Q8, substantially lower RAM, slightly better decode, and lower memory-pressure risk. Q8's prefill result is worth profiling further, but ~11 s cold-ready and ~4.5 GiB peak PSS make it a difficult broad default.

### Threading and lifecycle

llama.cpp operations are serialized off the main thread through a single limited-parallelism coroutine dispatcher. That keeps Compose responsive and prevents races between stateful load/reset/generate/unload operations.

I would not unload after every request. I would lazy-load when AI is needed, keep the model warm during an active task/conversation, use an idle/background grace period, unload on long idle or meaningful memory pressure when appropriate, and always recover cleanly after process death.

For an agent I would not blindly unload in `onStop()` because switching to another app may be part of the active workflow.

### Play Store model delivery

For a Play Store build, I would first evaluate **Play Asset Delivery (PAD)** instead of bundling a 1–2 GB GGUF in the base app. A primary model is a candidate for **fast-follow** delivery; optional models are candidates for **on-demand** delivery.

### CPU / GPU / NPU

All reported numbers use the same **CPU / 4-thread** backend for an apples-to-apples comparison. With more time I would benchmark GPU/Vulkan and supported mobile accelerator paths separately, especially for prefill vs decode.

---

# Part 2 — Accessibility Service

## Goal and implementation

The demo intentionally does only one deterministic task:

1. User enables Ordo's `AccessibilityService`.
2. Ordo opens Android Settings.
3. The service reads/logs the accessibility hierarchy.
4. It navigates to Bluetooth using `AccessibilityNodeInfo`.
5. It checks the switch state and turns Bluetooth ON only if currently OFF.

On the tested Pixel 6 Pro the path was:

```text
Settings
→ Connected devices
→ Connection preferences
→ Bluetooth
→ Use Bluetooth
```

Important findings:

- The visible text node is often not clickable, so the implementation walks up to an enabled clickable parent.
- Accessibility events are asynchronous/repeated; transition events can briefly expose previous-screen nodes.
- Navigation therefore searches the deepest destination first: `Use Bluetooth` → `Bluetooth` → `Connection preferences` → `Connected devices`.
- On the Bluetooth screen the service reads `android:id/switch_widget.isChecked` before acting.
- The pending action is cleared **before** clicking, preventing subsequent events from toggling Bluetooth back off.

## Portability across devices / Android versions

The prototype was validated on a **Pixel 6 Pro** and is not expected to be universally portable as-is. Other Android versions/OEMs may expose different Settings paths, labels, IDs, clickable-parent structure or accessibility hierarchy. The current English matchers also fail if Settings is localized.

For production I would replace the fixed path with screen/state recognition using multiple signals: text/content description, known view IDs where available, node type/state, hierarchy context, and expected pre/post-conditions. If the target cannot be identified confidently, the automation should fail safely instead of guessing.

## Accessibility policy and consent

The Android Studio Accessibility API policy warning is relevant and should not simply be suppressed.

In this demo the user explicitly requests **“Open Settings & turn Bluetooth on”**, and the service performs that narrow deterministic action.

For a Play Store release, current Google Play policy requires non-accessibility-tool apps using AccessibilityService to provide clear in-app disclosure, affirmative consent, and appropriate Play Console/listing documentation, and to prefer narrower APIs when possible. Google Play also prohibits Accessibility usage where an app **autonomously initiates, plans and executes actions or decisions**; deterministic rule-based automation is treated differently.

---

# What failed / limitations

- Dynamic llama.cpp CPU backends were initially not found in the expected extracted native-library directory; `useLegacyPackaging = true` fixed the current wrapper.
- Loading a second model while the first model/context was still alive failed; switching now copies first, `cleanUp()`s the current model, then loads the replacement.
- Accessibility navigation initially got stuck because previous-screen labels survived transition events; deepest-first matching fixed the observed Pixel flow.
- `dumpsys meminfo` provides sampled observations, not a guaranteed instantaneous peak.
- Fresh process does not guarantee storage-cold model pages because OS page cache may help.
- Battery counters are useful device measurements, not laboratory-grade energy instrumentation.
- Thermal correlation is strong but not experimentally isolated.
- SmolLM stopped at EOS after 31 tokens in two baseline runs, so total completion time is not directly comparable with 128-token runs.
- The Q8 prefill advantage is measured; its kernel-level cause remains a hypothesis.
- Accessibility was tested on one Pixel environment and currently relies partly on English Settings labels.
- The take-home does not implement the full production Accessibility disclosure/consent UX; that requirement is documented above.

---

# What I would try next week

### LLM

- Profile Q4 vs Q8 kernel/backend selection.
- Benchmark prompt/KV reuse instead of resetting every request.
- Test smaller contexts/models against the `<2 s` TTFT target.
- Compare CPU with GPU/Vulkan and supported accelerator runtimes using identical prompts.
- Add another device tier.

### Accessibility

- Replace fixed labels with explicit screen/state recognition.
- Add retry limits, timeouts and post-condition validation.
- Test another OEM, multiple Android versions and non-English locales.
- Add scrolling/off-screen discovery and user cancellation.
- Define an action-risk model so sensitive/irreversible actions require explicit confirmation.
- Validate any production automation design against Play Accessibility policy.

---
