package com.tm.ordotakehome.benchmark

object BenchmarkPrompts {

    const val PREDICT_LENGTH = 128
    const val SUSTAINED_RUN_COUNT = 20

    val LONG_PROMPT = """
        Android applications operate within a resource-constrained environment where CPU time, memory, battery power, storage, and thermal capacity must all be considered. Unlike desktop computers, smartphones are expected to remain responsive while performing many tasks simultaneously. Applications may be running in the foreground, background services may be active, notifications may arrive, cameras may be operating, and the operating system may reclaim resources whenever necessary.

        Running artificial intelligence models directly on smartphones introduces additional constraints. Large language models contain hundreds of millions or billions of parameters. These parameters need to be stored on the device and made available to the inference runtime. Loading model weights consumes memory, while repeatedly evaluating the neural network consumes processor resources and memory bandwidth. Quantization is therefore commonly used to reduce the size of model weights. For example, converting model weights from higher numerical precision to eight-bit or four-bit representations can substantially reduce storage and memory requirements.

        Reducing model size does not necessarily reduce inference latency by exactly the same proportion. Different parts of model execution are limited by different hardware characteristics. Some operations may primarily depend on arithmetic throughput, while others may depend more heavily on memory bandwidth, cache behavior, or the efficiency of the inference runtime. Hardware architecture also matters because different processors contain different combinations of general-purpose CPU cores, graphics processors, neural accelerators, caches, and memory controllers.

        Transformer-based language models generally perform inference in two important phases. During the prefill phase, the model processes the input prompt. Because many prompt tokens are known in advance, the runtime can process multiple tokens together and exploit parallel computation. During the decode phase, the model generates new tokens one after another. Each generated token depends on information from previous tokens, which limits the amount of parallelism available during generation.

        Transformers use an attention mechanism that allows tokens to consider information from other tokens in the sequence. During autoregressive generation, repeatedly recalculating all previous attention information would be expensive. Implementations therefore maintain a key-value cache, commonly called the KV cache, containing intermediate information from earlier tokens. This improves generation speed but increases memory usage as the context grows.

        Mobile operating systems introduce another layer of complexity. Android manages application processes according to available system resources and user activity. A foreground application typically receives higher priority than a background application, but processes may still experience memory pressure or thermal throttling. If a device becomes hot during sustained inference, the processor may reduce its operating frequency to control temperature. As a result, an application that initially produces tokens quickly may become slower after repeated generations.

        Battery consumption is also important for on-device artificial intelligence. Continuously running computationally intensive workloads can drain a battery quickly. An application should therefore avoid unnecessary inference, control how frequently models run, and release expensive resources when they are no longer needed. Developers must balance latency against energy consumption because maximizing instantaneous performance does not always provide the best overall user experience.

        For conversational applications, latency is particularly noticeable. A user may tolerate moderate token generation speed once text begins appearing on screen, but a long delay before the first token can make the application feel unresponsive. Time to first token is therefore an important product metric. It includes the time required to process the prompt and begin generating the response.

        On-device inference also offers advantages. User data can remain on the device rather than being transmitted to a remote server. Applications may continue functioning when an internet connection is unavailable, and server inference costs can potentially be reduced. However, these benefits come with engineering challenges involving model distribution, storage, compatibility, performance, memory management, battery consumption, and thermal behavior.

        **Based only on the information above, summarize the main trade-offs involved in running a language model locally on an Android smartphone. Keep your answer concise.**
    """.trimIndent()
}