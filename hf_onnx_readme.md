---
language:
- en
- fr
- de
- es
- it
- pt
- nl
- hi
license: apache-2.0
library_name: transformers.js
base_model:
- mistralai/Voxtral-Mini-3B-2507
pipeline_tag: audio-text-to-text
---

# Voxtral Mini 1.0 (3B) - 2507

Voxtral Mini is an enhancement of [Ministral 3B](https://mistral.ai/news/ministraux), incorporating state-of-the-art audio input capabilities while retaining best-in-class text performance. It excels at speech transcription, translation and audio understanding.

This repository contains ONNX weights for the original model, [mistralai/Voxtral-Mini-3B-2507](https://huggingface.co/mistralai/Voxtral-Mini-3B-2507).

Learn more about Voxtral in their blog post [here](https://mistral.ai/news/voxtral).

## Key Features

Voxtral builds upon Ministral-3B with powerful audio understanding capabilities.
- **Dedicated transcription mode**: Voxtral can operate in a pure speech transcription mode to maximize performance. By default, Voxtral automatically predicts the source audio language and transcribes the text accordingly
- **Long-form context**: With a 32k token context length, Voxtral handles audios up to 30 minutes for transcription, or 40 minutes for understanding
- **Built-in Q&A and summarization**: Supports asking questions directly through audio. Analyze audio and generate structured summaries without the need for separate ASR and language models
- **Natively multilingual**: Automatic language detection and state-of-the-art performance in the world’s most widely used languages (English, Spanish, French, Portuguese, Hindi, German, Dutch, Italian)
- **Function-calling straight from voice**: Enables direct triggering of backend functions, workflows, or API calls based on spoken user intents
- **Highly capable at text**: Retains the text understanding capabilities of its language model backbone, Ministral-3B

## Benchmark Results

### Audio

Average word error rate (WER) over the FLEURS, Mozilla Common Voice and Multilingual LibriSpeech benchmarks:

![image/png](https://cdn-uploads.huggingface.co/production/uploads/64161701107962562e9b1006/puASxtajF1lDeGYPrRK5y.png)

### Text

![image/png](https://cdn-uploads.huggingface.co/production/uploads/5dfcb1aada6d0311fd3d5448/iH9V8JVtMoaGlqJd6FIri.png)

## Usage

**Notes**:

- `temperature=0.2` and `top_p=0.95` for chat completion (*e.g. Audio Understanding*) and `temperature=0.0` for transcription
- Multiple audios per message and multiple user turns with audio are supported
- System prompts are not yet supported


### Transformers.js

#### Online demo

Try it out with our [online demo](https://huggingface.co/spaces/webml-community/Voxtral-WebGPU):

<video controls src="https://cdn-uploads.huggingface.co/production/uploads/61b253b7ac5ecaae3d1efe0c/3z0psEz3VS4kbscvXEE4n.mp4"></video>


#### Code snippets

If you haven't already, you can install the [Transformers.js](https://huggingface.co/docs/transformers.js) JavaScript library from [NPM](https://www.npmjs.com/package/@huggingface/transformers) using:
```bash
npm i @huggingface/transformers
```

**Example**: Transcription

```js
import { VoxtralForConditionalGeneration, VoxtralProcessor, TextStreamer, read_audio } from "@huggingface/transformers";

// Load the processor and model
const model_id = "onnx-community/Voxtral-Mini-3B-2507-ONNX";
const processor = await VoxtralProcessor.from_pretrained(model_id);
const model = await VoxtralForConditionalGeneration.from_pretrained(
    model_id,
    {
        dtype: {
            embed_tokens: "fp16", // "fp32", "fp16", "q8", "q4"
            audio_encoder: "q4", // "fp32", "fp16", "q8", "q4", "q4f16"
            decoder_model_merged: "q4", // "q4", "q4f16"
        },
        device: "webgpu",
    },
);

// Prepare the conversation
const conversation = [
    {
        "role": "user",
        "content": [
            { "type": "audio" },
            { "type": "text", "text": "lang:en [TRANSCRIBE]" },
        ],
    }
];
const text = processor.apply_chat_template(conversation, { tokenize: false });
const audio = await read_audio("http://huggingface.co/datasets/Xenova/transformers.js-docs/resolve/main/mlk.wav", 16000);
const inputs = await processor(text, audio);

// Generate the response
const generated_ids = await model.generate({
    ...inputs,
    max_new_tokens: 256,
    streamer: new TextStreamer(processor.tokenizer, { skip_special_tokens: true, skip_prompt: true }),
});

// Decode the generated tokens
const new_tokens = generated_ids.slice(null, [inputs.input_ids.dims.at(-1), null]);
const generated_texts = processor.batch_decode(
    new_tokens,
    { skip_special_tokens: true },
);
console.log(generated_texts[0]);
// I have a dream that one day this nation will rise up and live out the true meaning of its creed.
```


**Example**: Audio understanding

```js
import { VoxtralForConditionalGeneration, VoxtralProcessor, TextStreamer, read_audio } from "@huggingface/transformers";

// Load the processor and model
const model_id = "onnx-community/Voxtral-Mini-3B-2507-ONNX";
const processor = await VoxtralProcessor.from_pretrained(model_id);
const model = await VoxtralForConditionalGeneration.from_pretrained(
    model_id,
    {
        dtype: {
            embed_tokens: "fp16", // "fp32", "fp16", "q8", "q4"
            audio_encoder: "q4", // "fp32", "fp16", "q8", "q4", "q4f16"
            decoder_model_merged: "q4", // "q4", "q4f16"
        },
        device: "webgpu",
    },
);

// Prepare the conversation
const conversation = [
    {
        "role": "user",
        "content": [
            { "type": "audio" },
            { "type": "audio" },
            { "type": "text", "text": "Describe these two audio clips in detail." },
        ],
    }
];
const text = processor.apply_chat_template(conversation, { tokenize: false });
const audio = await Promise.all([
    read_audio("https://huggingface.co/datasets/Xenova/transformers.js-docs/resolve/main/jfk.wav", 16000),
    read_audio("https://huggingface.co/datasets/Xenova/transformers.js-docs/resolve/main/mlk.wav", 16000),
]);
const inputs = await processor(text, audio);

// Generate the response
const generated_ids = await model.generate({
    ...inputs,
    max_new_tokens: 256,
    streamer: new TextStreamer(processor.tokenizer, { skip_special_tokens: true, skip_prompt: true }),
});

// Decode the generated tokens
const new_tokens = generated_ids.slice(null, [inputs.input_ids.dims.at(-1), null]);
const generated_texts = processor.batch_decode(
    new_tokens,
    { skip_special_tokens: true },
);
console.log(generated_texts[0]);
// The first audio clip is a speech by a leader, likely a politician or a public figure, addressing a large audience. The speaker begins by encouraging the listeners to ask not what their country can do for them, but what they can do for their country. This is a call to action and a reminder of the individual's responsibility to contribute to the nation's well-being. The second audio clip is a passionate speech by a different leader, possibly a civil rights activist or a community organizer. This speaker expresses a dream of a nation that will rise up and live out the true meaning of its creed, suggesting a vision of a more just and equitable society.
```