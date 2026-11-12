# Training Ibiko's model: an adapter from what Ibiko knows

The device's model (Qwen3 1.7B, Q4_K_M or the Fast Q4_0) is a frozen 4-bit network. It can't be trained on a phone:
training needs the full-precision weights and a GPU. What evolves instead is a **LoRA adapter**: a small file (a few to
a few dozen MB) trained off the device and laid over the frozen weights when the model loads (`llm/Adapters`,
`ibiko_llm.cpp` `Engine::adapter`). The two together answer like the trained model; the downloaded file never changes.

The loop:

1. **Ibiko learns as it runs.** Ibiko 1 answers from its own memory (IBK); what it doesn't hold, it asks the device's
   model with what it already knows passed in. Every answer you check is kept, every one you cancel is marked false.
2. **Export.** Settings › Usage › Export saves `Downloads/Ibiko/ibiko-training-<date>.jsonl`: your confirmed answers
   first, then the model answers Ibiko kept. One `{"messages": [...]}` a line, the chat format training tools read.
3. **Train** (on a computer with a GPU, or a free Colab GPU), below.
4. **Install.** Settings › Usage › Install adapter, pick the `.gguf`. It is used from the model's next start. Holding
   the button puts the previous adapter back. One that doesn't fit the model is set aside and the model runs as it was.

## Step 3: train

The adapter must be trained on the same weights as the model on the device: `Qwen/Qwen3-1.7B` for both the default and
the Fast model (they are two 4-bit layouts of the same weights).

```bash
pip install "unsloth" "trl" "datasets"        # or: transformers peft trl datasets
```

```python
from unsloth import FastLanguageModel
from datasets import load_dataset
from trl import SFTTrainer, SFTConfig

model, tok = FastLanguageModel.from_pretrained("Qwen/Qwen3-1.7B", max_seq_length=2048, load_in_4bit=True)
model = FastLanguageModel.get_peft_model(model, r=16, lora_alpha=16, lora_dropout=0,
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"])
data = load_dataset("json", data_files="ibiko-training.jsonl", split="train")
data = data.map(lambda e: {"text": tok.apply_chat_template(e["messages"], tokenize=False)})
SFTTrainer(model=model, tokenizer=tok, train_dataset=data,
           args=SFTConfig(dataset_text_field="text", num_train_epochs=2, learning_rate=2e-4,
                          per_device_train_batch_size=4, output_dir="out")).train()
model.save_pretrained("ibiko-lora")             # the PEFT adapter (adapter_model.safetensors)
```

Then turn it into a GGUF adapter with llama.cpp's converter (from a llama.cpp checkout on the computer; the copy in this
app is the library only):

```bash
python convert_lora_to_gguf.py ibiko-lora --base Qwen/Qwen3-1.7B --outfile ibiko.adapter.gguf --outtype f16
```

Copy `ibiko.adapter.gguf` to the device and install it (step 4).

## What to expect

- A few hundred good examples change the style and the facts it repeats; they don't make the model smarter in general.
- Train on answers you checked. Answers from API models fall under the provider's terms: check them before training.
- Keep the previous adapter (the app does, once). If replies get worse, hold Install adapter to go back.

Status: the device side (export, install, load, take back, refusing a wrong file) is built and tested in the JVM and
in a desktop build of the engine. Training itself was not run here (no GPU in this environment).
