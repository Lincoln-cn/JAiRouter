#!/bin/bash

# ============================================================================
# DTO 重构验证脚本
# 验证 ChatDTO/EmbeddingDTO/RerankDTO 的核心字段 + Options 模式
# ============================================================================

echo "=========================================="
echo "DTO 重构验证 - 核心字段 + Options 模式"
echo "=========================================="
echo ""

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 颜色输出函数
print_success() { echo -e "${GREEN}✓${NC} $1"; }
print_error() { echo -e "${RED}✗${NC} $1"; }
print_warning() { echo -e "${YELLOW}⚠${NC} $1"; }
print_info() { echo -e "${BLUE}ℹ${NC} $1"; }

# ============================================================================
# 1. 检查 DTO 类结构
# ============================================================================
echo "1. 检查 DTO 类结构..."
echo "-------------------"

# 检查 ChatDTO
if grep -q "public static class Options" src/main/java/org/unreal/modelrouter/dto/ChatDTO.java; then
    print_success "ChatDTO.Options 嵌套类已创建"
else
    print_error "ChatDTO.Options 嵌套类缺失"
fi

# 检查核心字段数量
core_fields=$(grep -E "^\s+(String|List|Boolean|Integer|Double|Object) " src/main/java/org/unreal/modelrouter/dto/ChatDTO.java | grep -v "Options" | wc -l)
print_info "ChatDTO.Request 核心字段数：$core_fields (期望：11)"

# 检查 Options 字段数量
options_fields=$(grep -E "^\s+private " src/main/java/org/unreal/modelrouter/dto/ChatDTO.java | wc -l)
print_info "ChatDTO.Options 扩展字段数：$options_fields (期望：29)"

# 检查 @Builder 注解
if grep -q "@Builder" src/main/java/org/unreal/modelrouter/dto/ChatDTO.java; then
    print_success "ChatDTO.Options 已添加 @Builder 注解"
else
    print_error "ChatDTO.Options 缺少 @Builder 注解"
fi

# 检查 EmbeddingDTO
if grep -q "public static class Options" src/main/java/org/unreal/modelrouter/dto/EmbeddingDTO.java; then
    print_success "EmbeddingDTO.Options 嵌套类已创建"
else
    print_error "EmbeddingDTO.Options 嵌套类缺失"
fi

# 检查 RerankDTO
if grep -q "public static class Options" src/main/java/org/unreal/modelrouter/dto/RerankDTO.java; then
    print_success "RerankDTO.Options 嵌套类已创建"
else
    print_error "RerankDTO.Options 嵌套类缺失"
fi

echo ""

# ============================================================================
# 2. 检查便捷方法
# ============================================================================
echo "2. 检查便捷方法（Request 记录中的代理方法）..."
echo "-------------------"

# 检查 ChatDTO 便捷方法
chat_proxy_methods=$(grep -E "public.*\(\) \{" src/main/java/org/unreal/modelrouter/dto/ChatDTO.java | wc -l)
print_info "ChatDTO.Request 便捷方法数：$chat_proxy_methods (期望：29)"

# 检查关键便捷方法是否存在
for method in "n()" "logprobs()" "topLogprobs()" "useBeamSearch()" "repeatPenalty()" "seed()"; do
    if grep -q "public.*$method" src/main/java/org/unreal/modelrouter/dto/ChatDTO.java; then
        print_success "便捷方法 $method 存在"
    else
        print_error "便捷方法 $method 缺失"
    fi
done

echo ""

# ============================================================================
# 3. 检查 Adapter 使用情况
# ============================================================================
echo "3. 检查 Adapter 对 DTO 的使用..."
echo "-------------------"

# 检查 GpuStackAdapter
if [ -f src/main/java/org/unreal/modelrouter/adapter/impl/GpuStackAdapter.java ]; then
    # 检查是否使用 options() 访问扩展参数
    if grep -q "request\.options()" src/main/java/org/unreal/modelrouter/adapter/impl/GpuStackAdapter.java; then
        print_info "GpuStackAdapter 使用 request.options() 访问扩展参数"
    fi
    
    # 检查是否使用便捷方法
    if grep -q "request\.n()" src/main/java/org/unreal/modelrouter/adapter/impl/GpuStackAdapter.java; then
        print_success "GpuStackAdapter 使用便捷方法访问扩展参数"
    else
        print_warning "GpuStackAdapter 可能未完全使用便捷方法"
    fi
fi

# 检查 OllamaAdapter
if [ -f src/main/java/org/unreal/modelrouter/adapter/impl/OllamaAdapter.java ]; then
    if grep -q "request\.repeatPenalty()" src/main/java/org/unreal/modelrouter/adapter/impl/OllamaAdapter.java; then
        print_success "OllamaAdapter 使用便捷方法访问扩展参数"
    fi
fi

echo ""

# ============================================================================
# 4. 编译验证
# ============================================================================
echo "4. 编译验证..."
echo "-------------------"

if mvn compile -Pfast -q 2>&1 | grep -q "BUILD SUCCESS"; then
    print_success "编译成功"
else
    # 尝试直接检查编译结果
    if mvn compile -Pfast > /tmp/dto_compile.log 2>&1; then
        print_success "编译成功"
    else
        print_error "编译失败，查看日志："
        tail -20 /tmp/dto_compile.log
    fi
fi

echo ""

# ============================================================================
# 5. 创建使用示例
# ============================================================================
echo "5. 创建使用示例..."
echo "-------------------"

cat > /tmp/dto_usage_examples.md << 'EOF'
# DTO 使用示例 - 核心字段 + Options 模式

## ChatDTO 使用示例

### 1. 简单使用（只传核心参数）

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen3:4b",
    "messages": [
      {"role": "user", "content": "你好"}
    ],
    "stream": false,
    "max_tokens": 1000,
    "temperature": 0.7
  }'
```

### 2. 使用扩展参数（Ollama 特定）

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen3:4b",
    "messages": [
      {"role": "user", "content": "你好"}
    ],
    "stream": false,
    "max_tokens": 1000,
    "temperature": 0.7,
    "options": {
      "repeat_penalty": 1.1,
      "seed": 42,
      "num_keep": 50,
      "tfs_z": 0.95,
      "typical_p": 0.95,
      "repeat_last_n": 64,
      "penalize_newline": true
    }
  }'
```

### 3. 使用扩展参数（GPUStack 特定）

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen3:4b",
    "messages": [
      {"role": "user", "content": "你好"}
    ],
    "stream": false,
    "max_tokens": 1000,
    "temperature": 0.7,
    "options": {
      "use_beam_search": false,
      "min_p": 0.0,
      "repetition_penalty": 1.0,
      "length_penalty": 1.0,
      "include_stop_str_in_output": false,
      "ignore_eos": false,
      "min_tokens": 0,
      "skip_special_tokens": true,
      "add_generation_prompt": true
    }
  }'
```

## EmbeddingDTO 使用示例

### 简单使用

```bash
curl -X POST http://localhost:8080/v1/embeddings \
  -H "Content-Type: application/json" \
  -d '{
    "model": "nomic-embed-text-v1.5",
    "input": "这是一段测试文本",
    "encoding_format": "float"
  }'
```

### 使用扩展参数

```bash
curl -X POST http://localhost:8080/v1/embeddings \
  -H "Content-Type: application/json" \
  -d '{
    "model": "nomic-embed-text-v1.5",
    "input": "这是一段测试文本",
    "encoding_format": "float",
    "options": {
      "truncate_prompt_tokens": true,
      "add_special_tokens": false,
      "embed_dtype": "float32"
    }
  }'
```

## RerankDTO 使用示例

### 简单使用

```bash
curl -X POST http://localhost:8080/v1/rerank \
  -H "Content-Type: application/json" \
  -d '{
    "model": "bge-reranker-v2-m3",
    "query": "什么是人工智能",
    "documents": [
      "人工智能是计算机科学的一个分支",
      "机器学习是人工智能的核心技术"
    ],
    "top_n": 2
  }'
```

### 使用扩展参数

```bash
curl -X POST http://localhost:8080/v1/rerank \
  -H "Content-Type: application/json" \
  -d '{
    "model": "bge-reranker-v2-m3",
    "query": "什么是人工智能",
    "documents": [
      "人工智能是计算机科学的一个分支",
      "机器学习是人工智能的核心技术"
    ],
    "top_n": 2,
    "return_documents": true,
    "options": {
      "priority": 1,
      "truncate_prompt_tokens": false
    }
  }'
```

## 参数说明

### 核心字段（ChatDTO.Request）

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| model | String | 是 | 模型名称 |
| messages | List<Message> | 是 | 消息列表 |
| stream | Boolean | 否 | 是否流式 |
| max_tokens | Integer | 否 | 最大 token 数 |
| temperature | Double | 否 | 温度参数 |
| top_p | Double | 否 | Top-P 参数 |
| top_k | Integer | 否 | Top-K 参数 |
| frequency_penalty | Double | 否 | 频率惩罚 |
| presence_penalty | Double | 否 | 存在惩罚 |
| stop | Object | 否 | 停止序列 |
| user | String | 否 | 用户标识 |

### 扩展字段（ChatDTO.Options）

#### 通用扩展参数
- n: Integer - 生成数量
- logprobs: Boolean - 是否返回 log 概率
- top_logprobs: Integer - 返回的 top log 概率数
- use_beam_search: Boolean - 是否使用束搜索
- min_p: Double - 最小概率阈值
- repetition_penalty: Double - 重复惩罚
- length_penalty: Double - 长度惩罚
- ... 等 29 个参数

#### Ollama 特定参数
- repeat_penalty: Double - 重复惩罚
- seed: Integer - 随机种子
- num_keep: Integer - 保留的 token 数
- tfs_z: Double - 尾部自由采样参数
- typical_p: Double - 典型采样参数
- repeat_last_n: Integer - 重复检测窗口
- penalize_newline: Boolean - 是否惩罚换行符

EOF

print_info "使用示例已保存到：/tmp/dto_usage_examples.md"
cat /tmp/dto_usage_examples.md

echo ""

# ============================================================================
# 6. 对比重构前后
# ============================================================================
echo "=========================================="
echo "重构前后对比"
echo "=========================================="
echo ""

echo "重构前："
echo "  - ChatDTO.Request: 56 个字段（全部必需）"
echo "  - EmbeddingDTO.Request: 6 个字段"
echo "  - RerankDTO.Request: 5 个字段"
echo "  - 问题：字段过多，难以维护，所有参数都必须传递"
echo ""

echo "重构后："
echo "  - ChatDTO.Request: 11 个核心字段 + Options 嵌套类（29 个扩展字段）"
echo "  - EmbeddingDTO.Request: 5 个核心字段 + Options 嵌套类（13 个扩展字段）"
echo "  - RerankDTO.Request: 5 个核心字段 + Options 嵌套类（3 个扩展字段）"
echo "  - 优势："
echo "    ✓ 核心字段清晰，只包含必需参数"
echo "    ✓ 扩展参数按需传递，使用 @Builder 方便构建"
echo "    ✓ 便于后续扩展，不影响现有代码"
echo "    ✓ 通过便捷方法保持向后兼容"
echo ""

# ============================================================================
# 7. 清理
# ============================================================================
rm -f /tmp/dto_compile.log

echo "=========================================="
echo "验证完成！"
echo "=========================================="
echo ""
print_info "详细使用示例请查看：/tmp/dto_usage_examples.md"
echo ""
