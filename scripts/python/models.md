方法一：llama.cpp 直接跑（最推荐）针对 qwen3.5模型的调用方法。

1. 编译 llama.cpp
   首先你需要最新版 llama.cpp。如果你还没装过：

# 克隆最新代码

git clone <https://github.com/ggml-org/llama.cpp.git>
cd llama.cpp

# macOS / CPU 编译

cmake -B build -DGGML\_CUDA=OFF
cmake --build build --config Release -j

# 如果有 NVIDIA GPU，改成：

# cmake -B build -DGGML\_CUDA=ON

# cmake --build build --config Release -j

交互式对话（Non-Thinking 模式，默认）

./build/bin/llama-cli \
-m ./models/Qwen3.5-9B-Q4\_K\_M.gguf \
\--ctx-size 16384 \
-cnv

启用 Thinking 模式
⚠️ 划重点：Qwen3.5 小模型系列（0.8B - 9B）默认关闭了 Thinking（推理思考）模式！这和大模型（27B+）不一样。

如果你想让小模型也输出 <think>...</think> 推理过程，需要通过 llama-server 启动并传入额外参数：

./build/bin/llama-server \
-m ./models/Qwen3.5-9B-Q4\_K\_M.gguf \
\--ctx-size 16384 \
\--chat-template-kwargs '{"enable\_thinking":true}'
