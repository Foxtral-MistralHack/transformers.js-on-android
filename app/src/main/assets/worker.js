let env, pipeline;
let transcriber = null;

self.addEventListener('message', async (e) => {
  const { action, payload } = e.data;

  if (action === 'initialize') {
    const { modelPath, modelId } = payload;

    try {
      self.postMessage({ status: 'log', message: 'Worker loading transformers.js via dynamic import v3.8.1...' });
      const transformers = await import('https://cdn.jsdelivr.net/npm/@huggingface/transformers@3.8.1');

      env = transformers.env;
      pipeline = transformers.pipeline;

      // Force WASM execution for maximum stability
      env.backends.onnx.wasm.numThreads = 1;
      env.backends.onnx.wasm.simd = false;
      env.allowLocalModels = true;
      env.allowRemoteModels = false;
      env.localModelPath = modelPath;

      self.postMessage({ status: 'log', message: `Worker loading transcriber pipeline for ${modelId}...` });

      transcriber = await pipeline('automatic-speech-recognition', modelId, {
        device: "wasm",
        dtype: "q8",
        progress_callback: (info) => {
          if (info.status === 'progress' && Math.round(info.progress) % 20 === 0) {
            self.postMessage({ status: 'log', message: `[WASM] Loading ${info.file}: ${Math.round(info.progress)}%` });
          }
        }
      });

      self.postMessage({ status: 'ready' });
    } catch (error) {
      self.postMessage({ status: 'error', error: error.message || error });
    }
  }

  if (action === 'generate') {
    if (!transcriber) {
      self.postMessage({ status: 'error', error: "Transcriber not ready." });
      return;
    }

    try {
      const floatArrayToProcess = payload;

      self.postMessage({ status: 'log', message: "Evaluating model..." });

      const output = await transcriber(floatArrayToProcess);

      self.postMessage({ status: 'log', message: "Model generated output... decoding" });

      const resultText = output.text.trim();
      self.postMessage({ status: 'result', text: resultText });

    } catch (error) {
      self.postMessage({ status: 'error', error: error.message || error });
    }
  }
});
