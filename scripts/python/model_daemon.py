#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Living Agent Service - 模型守护进程 (优化版)
预加载所有AI模型并通过命名管道提供服务

公司前台双模型架构:
- Qwen3-0.6B: 沟通、表达、高效回复 (Layer 2 闲聊神经元)
- Qwen3.5-2B: 任务转达、工具调用、部门引导 (Layer 3 工具神经元)

支持的模型:
- ASR: Sherpa-NCNN SenseVoice
- LLM: Qwen3-0.6B-GGUF / Qwen3.5-2B-GGUF
- TTS: MeloTTS

增强功能:
- 双模型智能路由
- TTS缓存机制
- 快速问候响应(无需LLM)
- 会话历史管理
- 音频重采样(16kHz Opus兼容)
"""

import sys
import os
import re
import subprocess
import hashlib
import time
import json
import contextlib
import traceback
import threading
from collections import deque
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
from typing import Optional, Dict, List, Tuple, Any

os.environ['NUMBA_DISABLE_JIT'] = '0'
os.environ['NUMBA_CACHE_DIR'] = '/tmp/numba_cache'

import numpy as np

SHERPA_MODEL_DIR = os.environ.get('SHERPA_MODEL_DIR', 
    '/app/ai-models/sherpa-ncnn/sherpa-ncnn-sense-voice-zh-en-ja-ko-yue-2025-09-09')
QWEN3_MODEL_FILE = os.environ.get('QWEN3_MODEL_FILE', 
    '/app/ai-models/Qwen3-0.6B-GGUF/Qwen3-0.6B-Q8_0.gguf')
QWEN35_MODEL_FILE = os.environ.get('QWEN35_MODEL_FILE', 
    '/app/ai-models/Qwen3.5-2B-GGUF/Qwen3.5-2B-Q4_K_M.gguf')
MELOTTS_MODEL_DIR = os.environ.get('MELOTTS_MODEL_DIR', 
    '/app/ai-models/MeloTTS')
CAM_MODEL_DIR = os.environ.get('CAM_MODEL_DIR',
    '/app/ai-models/cam')
SPEAKER_DATA_FILE = os.environ.get('SPEAKER_DATA_FILE',
    '/app/data/speaker_embeddings.json')
SPEAKER_THRESHOLD = float(os.environ.get('SPEAKER_THRESHOLD', '0.33'))

CHAT_CONFIG = {
    'max_history_turns': 5,
    'max_tokens_chat': 512,
    'max_tokens_tool': 1024,
    'temperature_chat': 0.7,
    'temperature_tool': 0.3,
    'quick_response_timeout_ms': 3000,
    'enable_intent_classification': True,
    'enable_quick_greeting': True,
    'enable_tts_cache': True,
    'tts_cache_size': 100,
}

GREETINGS = {
    'morning': ['早上好', '早安', '早啊', '早'],
    'afternoon': ['下午好'],
    'evening': ['晚上好', '晚安'],
    'general': ['你好', '您好', 'hi', 'hello', 'hey', '哈喽', '嗨', '在吗', '在不在', '有人吗'],
}

TASK_KEYWORDS = {
    'query', '搜索', '查找', '获取', '执行', '运行', '调用',
    '创建', '删除', '修改', '更新', '发送', '接收',
    '打开', '关闭', '启动', '停止', '重启',
    'git', 'docker', '部署', '构建', '测试',
    '天气', '时间', '日期', '提醒', '闹钟',
    '邮件', '消息', '通知', '报告',
    '帮我', '请帮我', '帮我做', '帮我查', '帮我找',
    '转达', '告诉', '通知', '联系', '对接',
    '部门', '同事', '经理', '主管', '领导',
    '申请', '审批', '报销', '请假', '加班',
    '会议', '日程', '安排', '预约',
}

DEPARTMENT_KEYWORDS = {
    '技术部', '研发部', '开发部', '运维部', '测试部',
    '行政部', '人事部', '人力资源', '财务部', '法务部',
    '销售部', '市场部', '运营部', '客服部', '产品部',
    '设计部', '数据部', '安全部', '架构组',
}

COMPLEX_KEYWORDS = {
    '分析', '设计', '规划', '评估', '优化', '重构',
    '架构', '方案', '策略', '计划', '总结',
    '比较', '对比', '选择', '决策', '建议',
    '为什么', '怎么办', '如何处理', '怎么解决',
}


class DualModelIntentClassifier:
    """双模型意图分类器 - 区分沟通和任务转达"""
    
    class Intent:
        GREETING = 'greeting'
        CASUAL_CHAT = 'casual_chat'
        SIMPLE_QUESTION = 'simple_question'
        TASK_ROUTING = 'task_routing'
        TOOL_CALL = 'tool_call'
        COMPLEX_TASK = 'complex_task'
        UNKNOWN = 'unknown'
    
    class TargetModel:
        CHAT = 'chat'
        TOOL = 'tool'
        MAIN = 'main'
    
    @staticmethod
    def classify(text: str) -> Tuple[str, float, str, str]:
        """
        分类用户意图并返回目标模型
        
        Returns:
            Tuple[intent, confidence, reason, target_model]
            - intent: 意图类型
            - confidence: 置信度
            - reason: 分类原因
            - target_model: 目标模型 ('chat' -> Qwen3-0.6B, 'tool' -> Qwen3.5-2B, 'main' -> MainBrain)
        """
        if not text or not text.strip():
            return (DualModelIntentClassifier.Intent.UNKNOWN, 0.0, 
                    'Empty input', DualModelIntentClassifier.TargetModel.CHAT)
        
        normalized = text.strip().lower()
        
        if DualModelIntentClassifier._is_greeting(normalized):
            return (DualModelIntentClassifier.Intent.GREETING, 0.95, 
                    'Detected greeting', DualModelIntentClassifier.TargetModel.CHAT)
        
        if DualModelIntentClassifier._contains_department_keywords(normalized):
            return (DualModelIntentClassifier.Intent.TASK_ROUTING, 0.85, 
                    'Contains department keywords - routing to tool neuron', 
                    DualModelIntentClassifier.TargetModel.TOOL)
        
        if DualModelIntentClassifier._contains_task_keywords(normalized):
            return (DualModelIntentClassifier.Intent.TASK_ROUTING, 0.80, 
                    'Contains task keywords - routing to tool neuron', 
                    DualModelIntentClassifier.TargetModel.TOOL)
        
        if DualModelIntentClassifier._contains_tool_keywords(normalized):
            return (DualModelIntentClassifier.Intent.TOOL_CALL, 0.85, 
                    'Contains tool keywords', DualModelIntentClassifier.TargetModel.TOOL)
        
        if DualModelIntentClassifier._contains_complex_keywords(normalized):
            return (DualModelIntentClassifier.Intent.COMPLEX_TASK, 0.75, 
                    'Contains complex task keywords - may need main brain', 
                    DualModelIntentClassifier.TargetModel.MAIN)
        
        if DualModelIntentClassifier._is_simple_question(normalized):
            return (DualModelIntentClassifier.Intent.SIMPLE_QUESTION, 0.70, 
                    'Simple question pattern', DualModelIntentClassifier.TargetModel.CHAT)
        
        if DualModelIntentClassifier._is_casual_chat(normalized):
            return (DualModelIntentClassifier.Intent.CASUAL_CHAT, 0.65, 
                    'Casual chat pattern', DualModelIntentClassifier.TargetModel.CHAT)
        
        word_count = len(normalized.split())
        if word_count <= 5:
            return (DualModelIntentClassifier.Intent.SIMPLE_QUESTION, 0.60, 
                    'Short input', DualModelIntentClassifier.TargetModel.CHAT)
        
        return (DualModelIntentClassifier.Intent.CASUAL_CHAT, 0.50, 
                'Default to casual chat', DualModelIntentClassifier.TargetModel.CHAT)
    
    @staticmethod
    def _is_greeting(text: str) -> bool:
        import string
        clean = text.translate(str.maketrans('', '', string.punctuation + ' '))
        for category, greetings in GREETINGS.items():
            for g in greetings:
                if clean == g.lower() or clean.startswith(g.lower()):
                    return True
        return False
    
    @staticmethod
    def _contains_department_keywords(text: str) -> bool:
        for dept in DEPARTMENT_KEYWORDS:
            if dept in text:
                return True
        return False
    
    @staticmethod
    def _contains_task_keywords(text: str) -> bool:
        return any(kw in text for kw in TASK_KEYWORDS)
    
    @staticmethod
    def _contains_tool_keywords(text: str) -> bool:
        tool_patterns = ['查询', '搜索', '执行', '调用', '打开', '关闭', 
                        '启动', '停止', '发送', '获取', '设置', '配置']
        return any(p in text for p in tool_patterns)
    
    @staticmethod
    def _contains_complex_keywords(text: str) -> bool:
        return any(kw in text for kw in COMPLEX_KEYWORDS)
    
    @staticmethod
    def _is_simple_question(text: str) -> bool:
        return bool(re.search(r'[吗呢吧啊呀？?]$', text)) and len(text) < 50
    
    @staticmethod
    def _is_casual_chat(text: str) -> bool:
        patterns = ['怎么样', '如何', '什么意思', '怎么', '干嘛',
                   '是不是', '对不对', '好不好', '行不行', '可以吗', '能吗',
                   '觉得', '认为', '感觉', '想', '希望', '知道', '了解']
        return any(p in text for p in patterns)
    
    @staticmethod
    def should_use_chat_neuron(intent: str, target_model: str) -> bool:
        return target_model == DualModelIntentClassifier.TargetModel.CHAT


class QuickGreetingGenerator:
    """快速问候响应生成器 - 无需LLM调用"""
    
    _instance = None
    _lock = threading.Lock()
    
    def __new__(cls):
        if cls._instance is None:
            with cls._lock:
                if cls._instance is None:
                    cls._instance = super().__new__(cls)
        return cls._instance
    
    @staticmethod
    def generate(text: str) -> Optional[str]:
        lower = text.lower().strip()
        
        for g in GREETINGS['morning']:
            if g in lower:
                hour = time.localtime().tm_hour
                if 5 <= hour < 12:
                    return "早上好！今天有什么我可以帮助您的吗？"
        
        for g in GREETINGS['afternoon']:
            if g in lower:
                return "下午好！有什么我可以为您做的吗？"
        
        for g in GREETINGS['evening']:
            if g in lower:
                if '晚安' in lower:
                    return "晚安！祝您有个好梦。"
                return "晚上好！有什么我可以帮您的吗？"
        
        if re.match(r'.*在[吗呢].*', lower) or '在不在' in lower or '有人吗' in lower:
            return "我在的，有什么可以帮您？"
        
        for g in GREETINGS['general']:
            if g in lower:
                return "您好！我是公司的前台助手，有什么可以帮您的吗？"
        
        return None

class NeuronRouter:
    """神经网络路由器 - 路由到部门神经元或MainBrain"""
    
    DEPARTMENTS = {
        'tech': {
            'name': '技术部',
            'keywords': ['代码', '开发', '部署', '测试', '运维', 'bug', 'git', 'docker', '服务器', '数据库', 'api', '接口'],
            'channel': 'channel://tech/tasks'
        },
        'hr': {
            'name': '人力资源部',
            'keywords': ['请假', '考勤', '员工', '招聘', '入职', '离职', '薪资', '福利', 'hr'],
            'channel': 'channel://hr/tasks'
        },
        'fin': {
            'name': '财务部',
            'keywords': ['报销', '发票', '预算', '财务', '付款', '合同', '采购', '费用'],
            'channel': 'channel://fin/tasks'
        },
        'admin': {
            'name': '行政部',
            'keywords': ['行政', '文档', '流程', '会议室', '办公用品', '快递'],
            'channel': 'channel://admin/tasks'
        },
        'legal': {
            'name': '法务部',
            'keywords': ['合同', '法律', '合规', '法务', '协议', '条款'],
            'channel': 'channel://legal/tasks'
        },
        'sales': {
            'name': '销售部',
            'keywords': ['客户', '销售', '订单', '商机', '报价', 'crm'],
            'channel': 'channel://sales/tasks'
        },
        'cs': {
            'name': '客服部',
            'keywords': ['工单', '投诉', '客服', '反馈', '问题'],
            'channel': 'channel://cs/tasks'
        },
    }
    
    MAINBRAIN_KEYWORDS = [
        '分析', '设计', '规划', '评估', '优化', '重构', '架构',
        '方案', '策略', '计划', '总结', '比较', '对比', '选择',
        '决策', '建议', '为什么', '怎么办', '如何处理', '怎么解决',
        '跨部门', '协调', '战略', '重要'
    ]
    
    @classmethod
    def route_to_department(cls, text: str) -> Dict:
        """路由到部门神经元"""
        text_lower = text.lower()
        scores = {}
        
        for dept_key, dept_info in cls.DEPARTMENTS.items():
            score = 0
            for keyword in dept_info['keywords']:
                if keyword in text_lower:
                    score += 1
            if score > 0:
                scores[dept_key] = score
        
        if scores:
            best_dept = max(scores, key=scores.get)
            dept_info = cls.DEPARTMENTS[best_dept]
            return {
                'success': True,
                'department': best_dept,
                'department_name': dept_info['name'],
                'channel': dept_info['channel'],
                'score': scores[best_dept],
                'action': 'route_to_department'
            }
        
        return {
            'success': False,
            'action': 'no_department_match'
        }
    
    @classmethod
    def should_route_to_mainbrain(cls, text: str) -> bool:
        """判断是否需要路由到MainBrain"""
        text_lower = text.lower()
        return any(kw in text_lower for kw in cls.MAINBRAIN_KEYWORDS)
    
    @classmethod
    def route(cls, text: str, intent: str = None) -> Dict:
        """统一路由方法"""
        if cls.should_route_to_mainbrain(text):
            return {
                'success': True,
                'action': 'route_to_mainbrain',
                'target': 'mainbrain',
                'reason': 'Complex task requiring main brain processing',
                'api_endpoint': '/api/v1/mainbrain/chat'
            }
        
        dept_result = cls.route_to_department(text)
        if dept_result['success']:
            return dept_result
        
        return {
            'success': False,
            'action': 'no_routing_needed',
            'reason': 'No specific routing target matched'
        }


class TTSCache:
    """TTS缓存管理器 - MD5哈希缓存"""
    
    def __init__(self, max_size: int = 100):
        self.max_size = max_size
        self.cache: Dict[str, Dict[str, Any]] = {}
        self.lock = threading.Lock()
        self.hits = 0
        self.misses = 0
    
    def _generate_key(self, text: str, voice: str = 'default', speed: float = 1.0, 
                      language: str = 'zh') -> str:
        key_str = f"{text}|{voice}|{speed}|{language}"
        return hashlib.md5(key_str.encode()).hexdigest()
    
    def get(self, text: str, voice: str = 'default', speed: float = 1.0, 
            language: str = 'zh') -> Optional[Dict[str, Any]]:
        key = self._generate_key(text, voice, speed, language)
        with self.lock:
            if key in self.cache:
                self.hits += 1
                return self.cache[key].copy()
            self.misses += 1
            return None
    
    def set(self, text: str, audio_data: np.ndarray, sample_rate: int, 
            duration: float, voice: str = 'default', speed: float = 1.0, 
            language: str = 'zh') -> None:
        key = self._generate_key(text, voice, speed, language)
        with self.lock:
            if len(self.cache) >= self.max_size:
                oldest_key = next(iter(self.cache))
                del self.cache[oldest_key]
            
            self.cache[key] = {
                'audio': audio_data,
                'sample_rate': sample_rate,
                'duration': duration,
                'voice': voice,
                'language': language,
            }
    
    def get_stats(self) -> Dict[str, int]:
        with self.lock:
            total = self.hits + self.misses
            hit_rate = self.hits / total if total > 0 else 0
            return {
                'hits': self.hits,
                'misses': self.misses,
                'hit_rate': hit_rate,
                'cache_size': len(self.cache),
            }


class SessionHistory:
    """会话历史管理"""
    
    def __init__(self, max_turns: int = 5):
        self.max_turns = max_turns
        self.history: deque = deque(maxlen=max_turns * 2)
        self.created_at = time.time()
        self.last_accessed = time.time()
        self.message_count = 0
    
    def add_turn(self, role: str, content: str):
        self.history.append({'role': role, 'content': content})
        self.last_accessed = time.time()
        self.message_count += 1
    
    def get_history(self) -> List[Dict[str, str]]:
        return list(self.history)
    
    def clear(self):
        self.history.clear()
        self.message_count = 0
    
    def build_prompt(self, system_prompt: str, user_input: str) -> str:
        parts = [system_prompt, ""]
        
        if self.history:
            parts.append("--- 对话历史 ---")
            for turn in self.history:
                role = turn['role']
                content = turn['content']
                if role == 'user':
                    parts.append(f"用户：{content}")
                else:
                    parts.append(f"助手：{content}")
            parts.append("--- 当前问题 ---")
        
        parts.append(f"用户：{user_input}")
        parts.append("助手：")
        
        return "\n".join(parts)


class ModelManager:
    """模型管理器 - 支持双模型路由和声纹识别"""
    
    def __init__(self):
        self.sherpa_recognizer = None
        self.melotts_models = {}
        self.melotts_model = None
        self.llama_cli_path = None
        
        # CAM++ 声纹识别模型
        self.cam_model = None
        self.speaker_embeddings = {}
        self.speaker_profiles = {}
        
        self.models_loaded = {
            'sherpa': False,
            'qwen3': False,
            'qwen35': False,
            'melotts': False,
            'cam': False
        }
        
        self.sherpa_lock = threading.Lock()
        self.qwen3_lock = threading.Lock()
        self.qwen35_lock = threading.Lock()
        self.tts_lock = threading.Lock()
        self.cam_lock = threading.Lock()
        
        self.session_manager = None
        self.intent_classifier = DualModelIntentClassifier()
        self.greeting_generator = QuickGreetingGenerator()
        self.tts_cache = TTSCache(CHAT_CONFIG['tts_cache_size'])
        
        self.stats = {
            'total_requests': 0,
            'quick_responses': 0,
            'chat_model_calls': 0,
            'tool_model_calls': 0,
            'speaker_verifications': 0,
            'speaker_registrations': 0,
            'total_latency_ms': 0,
            'chat_latency_ms': 0,
            'tool_latency_ms': 0,
        }
        self.stats_lock = threading.Lock()
    
    @contextlib.contextmanager
    def suppress_stdout(self):
        old_stdout = sys.stdout
        devnull = open(os.devnull, "w")
        try:
            sys.stdout = devnull
            yield
        finally:
            sys.stdout = old_stdout
            devnull.close()
    
    def load_all_models(self):
        print("[ModelDaemon] 🚀 开始加载所有模型...", file=sys.stderr, flush=True)
        
        threads = []
        threads.append(threading.Thread(target=self._load_sherpa, name="Sherpa-Loader"))
        threads.append(threading.Thread(target=self._load_llm, name="LLM-Loader"))
        threads.append(threading.Thread(target=self._load_melotts, name="TTS-Loader"))
        threads.append(threading.Thread(target=self._load_cam, name="CAM-Loader"))
        
        for thread in threads:
            thread.start()
        for thread in threads:
            thread.join()
        
        loaded_count = sum(self.models_loaded.values())
        total_count = len(self.models_loaded)
        
        print(f"[ModelDaemon] 📊 模型加载完成: {loaded_count}/{total_count}", file=sys.stderr, flush=True)
        for model_name, loaded in self.models_loaded.items():
            status = "✅" if loaded else "❌"
            print(f"[ModelDaemon]   {model_name}: {status}", file=sys.stderr, flush=True)
        
        return loaded_count > 0
    
    def _load_sherpa(self):
        print("[ModelDaemon] 🎙️ 加载 Sherpa-NCNN ASR...", file=sys.stderr, flush=True)
        try:
            import sherpa_ncnn
            
            model_dir = Path(SHERPA_MODEL_DIR)
            if not model_dir.exists():
                print(f"[ModelDaemon] ❌ Sherpa模型目录不存在: {model_dir}", file=sys.stderr, flush=True)
                return
            
            import multiprocessing
            cpu_count = multiprocessing.cpu_count()
            thread_count = max(2, min(cpu_count, 4))
            
            config = sherpa_ncnn.OfflineRecognizerConfig(
                model_config=sherpa_ncnn.OfflineModelConfig(
                    sense_voice=sherpa_ncnn.OfflineSenseVoiceModelConfig(
                        model_dir=str(model_dir),
                        use_itn=True,
                    ),
                    tokens=str(model_dir / "tokens.txt"),
                    num_threads=thread_count,
                    debug=False,
                )
            )
            
            if config.validate():
                self.sherpa_recognizer = sherpa_ncnn.OfflineRecognizer(config)
                self.models_loaded['sherpa'] = True
                print("[ModelDaemon] ✅ Sherpa-NCNN 加载成功", file=sys.stderr, flush=True)
            else:
                print("[ModelDaemon] ❌ Sherpa-NCNN 配置验证失败", file=sys.stderr, flush=True)
                
        except ImportError:
            print("[ModelDaemon] ❌ sherpa_ncnn 未安装", file=sys.stderr, flush=True)
        except Exception as e:
            print(f"[ModelDaemon] ❌ Sherpa-NCNN 加载失败: {str(e)}", file=sys.stderr, flush=True)
    
    def _load_llm(self):
        print("[ModelDaemon] 🤖 加载 LLM 模型 (双模型架构)...", file=sys.stderr, flush=True)
        
        llama_cpp_path = os.environ.get('LLAMA_CPP_PATH', '/opt/llama.cpp')
        llama_cli_path = os.path.join(llama_cpp_path, 'build', 'bin', 'llama-cli')
        
        if os.path.exists(llama_cli_path):
            print(f"[ModelDaemon] 使用 llama.cpp CLI: {llama_cli_path}", file=sys.stderr, flush=True)
            self.llama_cli_path = llama_cli_path
        else:
            self.llama_cli_path = None
            print("[ModelDaemon] ❌ llama.cpp CLI 未找到", file=sys.stderr, flush=True)
        
        if os.path.exists(QWEN3_MODEL_FILE):
            print(f"[ModelDaemon] ✅ Qwen3-0.6B 模型文件存在: {QWEN3_MODEL_FILE}", file=sys.stderr, flush=True)
        
        if os.path.exists(QWEN35_MODEL_FILE):
            print(f"[ModelDaemon] ✅ Qwen3.5-2B 模型文件存在: {QWEN35_MODEL_FILE}", file=sys.stderr, flush=True)
        
        # 两个模型都使用 llama.cpp CLI
        if self.llama_cli_path and os.path.exists(self.llama_cli_path):
            if os.path.exists(QWEN3_MODEL_FILE):
                self.models_loaded['qwen3'] = True
                print("[ModelDaemon] ✅ Qwen3-0.6B 将通过 CLI 使用 (沟通模型)", file=sys.stderr, flush=True)
            if os.path.exists(QWEN35_MODEL_FILE):
                self.models_loaded['qwen35'] = True
                print("[ModelDaemon] ✅ Qwen3.5-2B 将通过 CLI 使用 (任务转达模型)", file=sys.stderr, flush=True)
        else:
            print("[ModelDaemon] ❌ llama.cpp CLI 不可用，LLM 模型无法加载", file=sys.stderr, flush=True)
    
    def _load_melotts(self):
        print("[ModelDaemon] 🔊 加载 MeloTTS...", file=sys.stderr, flush=True)
        try:
            melotts_dir = Path(MELOTTS_MODEL_DIR)
            if not melotts_dir.exists():
                print(f"[ModelDaemon] ❌ MeloTTS目录不存在: {melotts_dir}", file=sys.stderr, flush=True)
                return
            
            import nltk
            nltk.downloader.Downloader._update_index = lambda self: None
            
            nltk_data_dirs = [
                '/root/nltk_data', '/usr/local/nltk_data', '/usr/local/share/nltk_data',
                '/usr/local/lib/nltk_data', '/usr/share/nltk_data', '/usr/lib/nltk_data',
                '/opt/nltk_data'
            ]
            for data_dir in nltk_data_dirs:
                if os.path.exists(data_dir):
                    nltk.data.path.insert(0, data_dir)
                    print(f"[ModelDaemon] ✅ 添加NLTK数据目录: {data_dir}", file=sys.stderr, flush=True)
            
            try:
                import jieba
                jieba.initialize()
                print("[ModelDaemon] ✅ jieba 分词库预加载成功", file=sys.stderr, flush=True)
            except Exception as e:
                print(f"[ModelDaemon] ⚠️ jieba 预加载失败: {str(e)}", file=sys.stderr, flush=True)
            
            sys.path.insert(0, str(melotts_dir / "MeloTTS"))
            from melo.api import TTS
            
            supported_languages = ['zh', 'en', 'fr', 'es', 'jp', 'kr']
            language_map = {'zh': 'ZH', 'en': 'EN', 'fr': 'FR', 'es': 'ES', 'jp': 'JP', 'kr': 'KR'}
            
            loaded_languages = []
            for lang_code in supported_languages:
                lang_dir = melotts_dir / lang_code
                if lang_dir.exists():
                    config_path = lang_dir / "config.json"
                    ckpt_path = lang_dir / "checkpoint.pth"
                    
                    if config_path.exists() and ckpt_path.exists():
                        try:
                            tts = TTS(
                                language=language_map[lang_code], 
                                device='cpu', 
                                use_hf=False, 
                                config_path=str(config_path), 
                                ckpt_path=str(ckpt_path)
                            )
                            tts.model = tts.model.float()
                            self.melotts_models[lang_code] = tts
                            loaded_languages.append(lang_code)
                            print(f"[ModelDaemon] ✅ MeloTTS {lang_code.upper()} 加载成功", file=sys.stderr, flush=True)
                        except Exception as e:
                            print(f"[ModelDaemon] ⚠️ MeloTTS {lang_code} 加载失败: {e}", file=sys.stderr, flush=True)
            
            if 'zh' in self.melotts_models:
                self.melotts_model = self.melotts_models['zh']
            
            if loaded_languages:
                self.models_loaded['melotts'] = True
                print(f"[ModelDaemon] ✅ MeloTTS 支持语言: {', '.join([l.upper() for l in loaded_languages])}", file=sys.stderr, flush=True)
                
        except ImportError as e:
            print(f"[ModelDaemon] ❌ MeloTTS 未安装: {e}", file=sys.stderr, flush=True)
        except Exception as e:
            print(f"[ModelDaemon] ❌ MeloTTS 加载失败: {str(e)}", file=sys.stderr, flush=True)
            traceback.print_exc(file=sys.stderr)
    
    def _load_cam(self):
        """加载CAM++声纹识别模型"""
        print("[ModelDaemon] 🎤 加载 CAM++ 声纹识别模型...", file=sys.stderr, flush=True)
        try:
            cam_dir = Path(CAM_MODEL_DIR)
            if not cam_dir.exists():
                print(f"[ModelDaemon] ❌ CAM++模型目录不存在: {cam_dir}", file=sys.stderr, flush=True)
                return
            
            model_file = cam_dir / "campplus_cn_en_common.pt"
            if not model_file.exists():
                print(f"[ModelDaemon] ❌ CAM++模型文件不存在: {model_file}", file=sys.stderr, flush=True)
                return
            
            try:
                import torch
                TORCH_AVAILABLE = True
            except ImportError:
                TORCH_AVAILABLE = False
                print("[ModelDaemon] ⚠️ PyTorch未安装，CAM++模型无法加载", file=sys.stderr, flush=True)
                return
            
            try:
                from funasr import AutoModel
                FUNASR_AVAILABLE = True
            except ImportError:
                FUNASR_AVAILABLE = False
                print("[ModelDaemon] ⚠️ FunASR未安装，CAM++模型无法加载", file=sys.stderr, flush=True)
                return
            
            device = "cuda" if torch.cuda.is_available() else "cpu"
            print(f"[ModelDaemon] 🎤 使用设备: {device}", file=sys.stderr, flush=True)
            
            self.cam_model = AutoModel(
                model=str(cam_dir),
                device=device,
                disable_update=True,
                disable_log=True
            )
            
            self._load_speaker_data()
            
            self.models_loaded['cam'] = True
            print(f"[ModelDaemon] ✅ CAM++ 声纹识别模型加载成功 (设备: {device}, 已注册说话人: {len(self.speaker_embeddings)})", file=sys.stderr, flush=True)
            
        except Exception as e:
            print(f"[ModelDaemon] ❌ CAM++ 模型加载失败: {str(e)}", file=sys.stderr, flush=True)
            traceback.print_exc(file=sys.stderr)
    
    def _load_speaker_data(self):
        """加载已保存的声纹数据"""
        if os.path.exists(SPEAKER_DATA_FILE):
            try:
                with open(SPEAKER_DATA_FILE, 'r') as f:
                    data = json.load(f)
                    for speaker_id, spk_data in data.items():
                        if 'embedding' in spk_data:
                            spk_data['embedding'] = np.array(spk_data['embedding'], dtype=np.float32)
                    self.speaker_embeddings = data
                    print(f"[ModelDaemon] ✅ 加载 {len(self.speaker_embeddings)} 个已注册说话人", file=sys.stderr, flush=True)
            except Exception as e:
                print(f"[ModelDaemon] ⚠️ 加载声纹数据失败: {e}", file=sys.stderr, flush=True)
                self.speaker_embeddings = {}
    
    def _save_speaker_data(self):
        """保存声纹数据到文件"""
        try:
            os.makedirs(os.path.dirname(SPEAKER_DATA_FILE), exist_ok=True)
            data_to_save = {}
            for speaker_id, spk_data in self.speaker_embeddings.items():
                data_to_save[speaker_id] = {
                    'name': spk_data.get('name', speaker_id),
                    'embedding': spk_data['embedding'].tolist() if isinstance(spk_data['embedding'], np.ndarray) else spk_data['embedding'],
                    'audio_path': spk_data.get('audio_path', ''),
                    'registered_at': spk_data.get('registered_at', ''),
                    'profile': spk_data.get('profile', {})
                }
            with open(SPEAKER_DATA_FILE, 'w') as f:
                json.dump(data_to_save, f)
        except Exception as e:
            print(f"[ModelDaemon] ⚠️ 保存声纹数据失败: {e}", file=sys.stderr, flush=True)
    
    def extract_speaker_embedding(self, audio_path):
        """提取说话人embedding"""
        if not self.models_loaded['cam'] or self.cam_model is None:
            return None
        
        try:
            with self.cam_lock:
                result = self.cam_model.generate(input=audio_path)
                if result and len(result) > 0:
                    emb = None
                    if 'spk_embedding' in result[0]:
                        emb = result[0]['spk_embedding']
                    elif 'embedding' in result[0]:
                        emb = result[0]['embedding']
                    
                    if emb is not None:
                        if isinstance(emb, np.ndarray):
                            return emb.flatten().astype(np.float32)
                        elif hasattr(emb, 'detach'):
                            try:
                                return emb.detach().cpu().numpy().flatten().astype(np.float32)
                            except RuntimeError:
                                return np.array(emb.detach().cpu().tolist(), dtype=np.float32).flatten()
            return None
        except Exception as e:
            print(f"[ModelDaemon] ⚠️ 提取embedding失败: {e}", file=sys.stderr, flush=True)
            return None
    
    def cosine_similarity(self, a, b):
        """计算余弦相似度"""
        if a is None or b is None:
            return 0.0
        dot_product = np.dot(a, b)
        norm_a = np.linalg.norm(a)
        norm_b = np.linalg.norm(b)
        if norm_a == 0 or norm_b == 0:
            return 0.0
        return float(dot_product / (norm_a * norm_b))
    
    def register_speaker(self, audio_path, speaker_id, name=None, profile=None):
        """注册说话人声纹"""
        if not self.models_loaded['cam']:
            return {"success": False, "message": "CAM++模型未加载"}
        
        if not os.path.exists(audio_path):
            return {"success": False, "message": f"音频文件不存在: {audio_path}"}
        
        embedding = self.extract_speaker_embedding(audio_path)
        if embedding is None:
            return {"success": False, "message": "无法提取声纹特征"}
        
        with self.cam_lock:
            self.speaker_embeddings[speaker_id] = {
                'embedding': embedding,
                'name': name or speaker_id,
                'audio_path': audio_path,
                'registered_at': str(os.path.getmtime(audio_path)),
                'profile': profile or {}
            }
            self._save_speaker_data()
        
        with self.stats_lock:
            self.stats['speaker_registrations'] += 1
        
        print(f"[ModelDaemon] ✅ 说话人注册成功: {speaker_id} ({name or speaker_id})", file=sys.stderr, flush=True)
        return {
            "success": True,
            "speaker_id": speaker_id,
            "name": name or speaker_id,
            "message": "说话人注册成功",
            "embedding_dimension": len(embedding)
        }
    
    def verify_speaker(self, audio_path, speaker_id=None, threshold=None):
        """验证说话人身份"""
        if not self.models_loaded['cam']:
            return {"success": False, "verified": False, "message": "CAM++模型未加载"}
        
        if not os.path.exists(audio_path):
            return {"success": False, "verified": False, "message": f"音频文件不存在: {audio_path}"}
        
        threshold = threshold or SPEAKER_THRESHOLD
        test_embedding = self.extract_speaker_embedding(audio_path)
        if test_embedding is None:
            return {"success": False, "verified": False, "message": "无法提取声纹特征"}
        
        with self.cam_lock:
            if speaker_id:
                if speaker_id not in self.speaker_embeddings:
                    return {"success": False, "verified": False, "message": f"说话人未注册: {speaker_id}"}
                
                stored_data = self.speaker_embeddings[speaker_id]
                similarity = self.cosine_similarity(test_embedding, stored_data['embedding'])
                verified = similarity >= threshold
                
                with self.stats_lock:
                    self.stats['speaker_verifications'] += 1
                
                return {
                    "success": True,
                    "verified": verified,
                    "speaker_id": speaker_id,
                    "name": stored_data.get('name', speaker_id),
                    "similarity": similarity,
                    "threshold": threshold,
                    "profile": stored_data.get('profile', {}),
                    "message": "说话人验证通过" if verified else "说话人验证失败"
                }
            else:
                if not self.speaker_embeddings:
                    return {"success": True, "verified": False, "message": "无已注册说话人"}
                
                results = []
                for sid, data in self.speaker_embeddings.items():
                    similarity = self.cosine_similarity(test_embedding, data['embedding'])
                    results.append({
                        "speaker_id": sid,
                        "name": data.get('name', sid),
                        "similarity": similarity
                    })
                
                results.sort(key=lambda x: x['similarity'], reverse=True)
                best_match = results[0]
                verified = best_match['similarity'] >= threshold
                
                with self.stats_lock:
                    self.stats['speaker_verifications'] += 1
                
                if verified:
                    stored_data = self.speaker_embeddings[best_match['speaker_id']]
                    return {
                        "success": True,
                        "verified": True,
                        "speaker_id": best_match['speaker_id'],
                        "name": best_match['name'],
                        "similarity": best_match['similarity'],
                        "threshold": threshold,
                        "profile": stored_data.get('profile', {}),
                        "message": f"识别为: {best_match['name']}"
                    }
                else:
                    return {
                        "success": True,
                        "verified": False,
                        "similarity": best_match['similarity'],
                        "threshold": threshold,
                        "message": "未找到匹配的说话人"
                    }
    
    def recognize_audio(self, audio_path):
        if not self.models_loaded['sherpa']:
            return {"success": False, "error": "Sherpa模型未加载"}
        
        try:
            import soundfile as sf
            with self.sherpa_lock:
                audio, sample_rate = sf.read(audio_path, dtype='float32')
                if len(audio.shape) > 1:
                    audio = audio.mean(axis=1)
                
                stream = self.sherpa_recognizer.create_stream()
                stream.accept_waveform(sample_rate, audio)
                self.sherpa_recognizer.decode_stream(stream)
                text = stream.result.text.strip()
                
                return {"success": True, "text": text, "model": "sherpa"}
        except Exception as e:
            return {"success": False, "error": f"ASR失败: {str(e)}"}
    
    def classify_intent(self, text: str) -> Dict:
        """分类用户意图并返回目标模型"""
        intent, confidence, reason, target_model = self.intent_classifier.classify(text)
        return {
            "intent": intent,
            "confidence": confidence,
            "reason": reason,
            "target_model": target_model,
            "should_use_chat": target_model == DualModelIntentClassifier.TargetModel.CHAT
        }
    
    def generate_quick_greeting(self, text: str) -> Optional[str]:
        """生成快速问候响应 - 无需LLM"""
        if CHAT_CONFIG['enable_quick_greeting']:
            return self.greeting_generator.generate(text)
        return None
    
    def _build_chat_system_prompt(self) -> str:
        """构建沟通模型系统提示词 - Qwen3-0.6B"""
        return """你是公司的前台接待，负责接待访客和日常问候。

角色定位：
- 公司形象代表，热情友好的第一接触点
- 了解公司文化，但不涉及机密信息
- 专注于表达和高效回复，不处理专业业务

工作方式：
- 快速响应，简洁明了
- 礼貌热情，乐于助人
- 遇到专业问题，告知访客将转接专业人员处理

注意：
- 你只负责日常问候和简单交流
- 工具调用、部门引导等专业事务由其他系统处理"""
    
    def _build_tool_system_prompt(self) -> str:
        """构建任务转达模型系统提示词 - Qwen3.5-2B"""
        return """你是公司的前台任务转达助手，负责将访客需求转达给相应部门。

角色定位：
- 任务分析和部门路由专家
- 理解访客需求并匹配到正确的部门或人员
- 不处理具体业务，只负责转达和引导

部门列表：
- 技术部：代码、开发、部署、测试、运维
- 行政部：文档、流程、行政事务
- 财务部：报销、发票、预算
- 人事部：招聘、考勤、绩效
- 法务部：合同、合规检查
- 销售部：客户、订单、市场
- 客服部：工单、问题解答
- 运营部：数据分析、运营策略

工作方式：
1. 分析访客需求的关键词和意图
2. 确定应该转达的部门或人员
3. 简洁告知访客将转达给谁处理

注意：
- 只负责转达，不执行具体操作
- 复杂问题建议访客直接联系相关部门"""
    
    def generate_text(self, prompt, model='qwen3', max_tokens=1000, temperature=0.7, session_id=None):
        start_time = time.time()
        
        with self.stats_lock:
            self.stats['total_requests'] += 1
        
        model_path = QWEN3_MODEL_FILE if model == 'qwen3' else QWEN35_MODEL_FILE
        model_key = model
        model_lock = self.qwen3_lock if model == 'qwen3' else self.qwen35_lock
        
        if not self.models_loaded.get(model_key):
            if model == 'qwen3' and self.models_loaded.get('qwen35'):
                model_path = QWEN35_MODEL_FILE
                model_key = 'qwen35'
                model_lock = self.qwen35_lock
            elif model == 'qwen35' and self.models_loaded.get('qwen3'):
                model_path = QWEN3_MODEL_FILE
                model_key = 'qwen3'
                model_lock = self.qwen3_lock
            else:
                return {"success": False, "error": "LLM模型未加载"}
        
        try:
            with model_lock:
                if not self.llama_cli_path or not os.path.exists(self.llama_cli_path):
                    return {"success": False, "error": "llama.cpp CLI 不可用"}
                
                ctx_size = 512 if model_key == 'qwen3' else 16384
                result = subprocess.run(
                    [
                        self.llama_cli_path,
                        '-m', model_path,
                        '--ctx-size', str(ctx_size),
                        '-n', str(max_tokens),
                        '--temp', str(temperature),
                        '--top-p', '0.9',
                        '-p', prompt,
                        '-no-cnv'
                    ],
                    capture_output=True,
                    text=True,
                    timeout=120
                )
                
                if result.returncode == 0:
                    response = result.stdout.strip()
                    if response.startswith(prompt):
                        response = response[len(prompt):].strip()
                    
                    latency = int((time.time() - start_time) * 1000)
                    with self.stats_lock:
                        self.stats['total_latency_ms'] += latency
                        if model_key == 'qwen3':
                            self.stats['chat_model_calls'] += 1
                            self.stats['chat_latency_ms'] += latency
                        else:
                            self.stats['tool_model_calls'] += 1
                            self.stats['tool_latency_ms'] += latency
                    
                    return {"success": True, "text": response, "model": model_key, "backend": "llama.cpp CLI", "latency_ms": latency}
                else:
                    return {"success": False, "error": f"llama.cpp CLI 失败: {result.stderr}"}
        except subprocess.TimeoutExpired:
            return {"success": False, "error": "LLM推理超时"}
        except Exception as e:
            return {"success": False, "error": f"LLM失败: {str(e)}"}
    
    def generate_chat_response(self, session_id: str, user_input: str, history: List[Dict] = None) -> Dict:
        """生成闲聊响应（带意图识别和快速响应）"""
        start_time = time.time()
        
        with self.stats_lock:
            self.stats['total_requests'] += 1
        
        intent = 'unknown'
        confidence = 0.0
        target_model = DualModelIntentClassifier.TargetModel.CHAT
        
        if CHAT_CONFIG['enable_intent_classification']:
            intent_result = self.classify_intent(user_input)
            intent = intent_result['intent']
            confidence = intent_result['confidence']
            target_model = intent_result['target_model']
            
            if intent == DualModelIntentClassifier.Intent.GREETING:
                quick_response = self.generate_quick_greeting(user_input)
                if quick_response:
                    latency = int((time.time() - start_time) * 1000)
                    with self.stats_lock:
                        self.stats['quick_responses'] += 1
                        self.stats['total_latency_ms'] += latency
                    return {
                        "success": True,
                        "text": quick_response,
                        "model": "quick-greeting",
                        "intent": intent,
                        "confidence": confidence,
                        "latency_ms": latency
                    }
            
            if target_model == DualModelIntentClassifier.TargetModel.TOOL:
                system_prompt = self._build_tool_system_prompt()
                if history:
                    full_prompt = self._build_prompt_with_history(system_prompt, user_input, history)
                else:
                    full_prompt = f"{system_prompt}\n\n用户：{user_input}\n助手："
                
                result = self.generate_text(
                    full_prompt,
                    model='qwen35',
                    max_tokens=CHAT_CONFIG['max_tokens_tool'],
                    temperature=CHAT_CONFIG['temperature_tool'],
                    session_id=session_id
                )
                
                if result.get('success'):
                    result['intent'] = intent
                    result['confidence'] = confidence
                    result['target_model'] = 'tool-neuron'
                    
                    routing_result = NeuronRouter.route(user_input, intent)
                    if routing_result['success']:
                        result['routing'] = routing_result
                
                return result
            
            if target_model == DualModelIntentClassifier.TargetModel.MAIN:
                routing_result = NeuronRouter.route(user_input, intent)
                return {
                    "success": True,
                    "text": "",
                    "model": "router",
                    "intent": intent,
                    "confidence": confidence,
                    "routing_suggestion": "main-brain",
                    "should_route": True,
                    "routing": routing_result
                }
        
        system_prompt = self._build_chat_system_prompt()
        
        if history:
            full_prompt = self._build_prompt_with_history(system_prompt, user_input, history)
        else:
            full_prompt = f"{system_prompt}\n\n用户：{user_input}\n助手："
        
        result = self.generate_text(
            full_prompt,
            model='qwen3',
            max_tokens=CHAT_CONFIG['max_tokens_chat'],
            temperature=CHAT_CONFIG['temperature_chat'],
            session_id=session_id
        )
        
        if result.get('success'):
            result['intent'] = intent
            result['confidence'] = confidence
            result['target_model'] = 'chat-neuron'
        
        return result
    
    def _build_prompt_with_history(self, system_prompt: str, user_input: str, history: List[Dict]) -> str:
        parts = [system_prompt, ""]
        
        if history:
            parts.append("--- 对话历史 ---")
            for turn in history[-CHAT_CONFIG['max_history_turns']*2:]:
                role = turn.get('role', 'user')
                content = turn.get('content', '')
                if role == 'user':
                    parts.append(f"用户：{content}")
                else:
                    parts.append(f"助手：{content}")
            parts.append("--- 当前问题 ---")
        
        parts.append(f"用户：{user_input}")
        parts.append("助手：")
        
        return "\n".join(parts)
    
    def synthesize_speech(self, text, language='zh', speed=1.0, output_path=None):
        if not self.models_loaded['melotts']:
            return {"success": False, "error": "MeloTTS模型未加载"}
        
        if CHAT_CONFIG['enable_tts_cache']:
            cached = self.tts_cache.get(text, language=language, speed=speed)
            if cached:
                audio_data = cached['audio']
                sample_rate = cached['sample_rate']
                duration = cached['duration']
                
                audio_int16 = (audio_data * 32767).astype(np.int16)
                
                if output_path:
                    try:
                        import soundfile as sf
                        sf.write(output_path, audio_int16, sample_rate, subtype='PCM_16')
                    except Exception as e:
                        print(f"[ModelDaemon] ❌ 缓存音频保存失败: {str(e)}", file=sys.stderr, flush=True)
                
                return {
                    "success": True,
                    "duration": duration,
                    "sample_rate": sample_rate,
                    "model": "melotts-cached",
                    "lang_code": language,
                    "cache_hit": True,
                    "audio_data": audio_int16.tolist()
                }
        
        try:
            has_chinese = bool(re.search(r'[\u4e00-\u9fff]', text))
            has_english = bool(re.search(r'[a-zA-Z]', text))
            
            selected_model = self.melotts_model
            lang_code = 'zh'
            
            if has_english and not has_chinese and 'en' in self.melotts_models:
                selected_model = self.melotts_models['en']
                lang_code = 'en'
            
            with self.tts_lock:
                import tempfile
                import soundfile as sf
                
                with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as f:
                    temp_path = f.name
                
                try:
                    speaker_ids = selected_model.hps.data.spk2id
                    if hasattr(speaker_ids, 'get'):
                        speaker_id = speaker_ids.get('EN-US' if lang_code == 'en' else 'ZH', 0)
                    else:
                        speaker_id = 0
                    
                    selected_model.tts_to_file(text, speaker_id, temp_path, speed=speed)
                    
                    audio, sample_rate = sf.read(temp_path)
                    duration = len(audio) / sample_rate
                    
                    target_sample_rate = 16000
                    if sample_rate != target_sample_rate:
                        try:
                            import scipy.signal as signal
                            target_length = int(len(audio) * target_sample_rate / sample_rate)
                            audio = signal.resample(audio, target_length)
                            sample_rate = target_sample_rate
                            duration = len(audio) / sample_rate
                        except Exception as e:
                            print(f"[ModelDaemon] ⚠️ 音频重采样失败: {e}", file=sys.stderr, flush=True)
                    
                    if CHAT_CONFIG['enable_tts_cache']:
                        self.tts_cache.set(text, audio, sample_rate, duration, language=lang_code, speed=speed)
                    
                    audio_int16 = (audio * 32767).astype(np.int16)
                    
                    result = {
                        "success": True,
                        "duration": duration,
                        "sample_rate": sample_rate,
                        "model": "melotts",
                        "lang_code": lang_code,
                        "cache_hit": False,
                        "audio_data": audio_int16.tolist()
                    }                    
                    if output_path:
                        sf.write(output_path, audio_int16, sample_rate, subtype='PCM_16')
                        result["output_path"] = output_path
                    
                    return result
                finally:
                    if os.path.exists(temp_path):
                        os.unlink(temp_path)
                        
        except Exception as e:
            return {"success": False, "error": f"TTS失败: {str(e)}"}
    
    def get_status(self):
        return {
            "models_loaded": self.models_loaded.copy(),
            "total_models": len(self.models_loaded),
            "loaded_count": sum(self.models_loaded.values()),
            "tts_cache_stats": self.tts_cache.get_stats() if CHAT_CONFIG['enable_tts_cache'] else None,
            "stats": self.stats.copy()
        }
    
    def get_stats(self) -> Dict:
        with self.stats_lock:
            stats = self.stats.copy()
            if stats['total_requests'] > 0:
                stats['avg_latency_ms'] = stats['total_latency_ms'] / stats['total_requests']
                stats['quick_response_rate'] = stats['quick_responses'] / stats['total_requests']
            if stats['chat_model_calls'] > 0:
                stats['avg_chat_latency_ms'] = stats['chat_latency_ms'] / stats['chat_model_calls']
            if stats['tool_model_calls'] > 0:
                stats['avg_tool_latency_ms'] = stats['tool_latency_ms'] / stats['tool_model_calls']
            return stats


class SessionManager:
    def __init__(self, model_manager, max_workers=10):
        self.model_manager = model_manager
        self.sessions = {}
        self.session_histories = {}
        self.lock = threading.Lock()
        self.executor = ThreadPoolExecutor(max_workers=max_workers)
        print("[SessionManager] 会话管理器初始化", file=sys.stderr, flush=True)
    
    def create_session(self, session_id):
        with self.lock:
            if session_id in self.sessions:
                return True
            request_pipe = f"/tmp/dialogue_daemon_request_{session_id}"
            response_pipe = f"/tmp/dialogue_daemon_response_{session_id}"
            
            try:
                for pipe in [request_pipe, response_pipe]:
                    if os.path.exists(pipe):
                        os.unlink(pipe)
                    os.mkfifo(pipe, 0o666)
                
                self.sessions[session_id] = {
                    "request_pipe": request_pipe,
                    "response_pipe": response_pipe,
                    "active": True
                }
                self.session_histories[session_id] = SessionHistory(CHAT_CONFIG['max_history_turns'])
                
                print(f"[SessionManager] 创建会话: {session_id}", file=sys.stderr, flush=True)
                return True
            except Exception as e:
                print(f"[SessionManager] 创建会话失败: {str(e)}", file=sys.stderr, flush=True)
                return False
    
    def destroy_session(self, session_id):
        with self.lock:
            if session_id not in self.sessions:
                return False
            
            session = self.sessions[session_id]
            session["active"] = False
            
            try:
                for pipe in [session["request_pipe"], session["response_pipe"]]:
                    if os.path.exists(pipe):
                        os.unlink(pipe)
                del self.sessions[session_id]
                if session_id in self.session_histories:
                    del self.session_histories[session_id]
                print(f"[SessionManager] 销毁会话: {session_id}", file=sys.stderr, flush=True)
                return True
            except Exception as e:
                print(f"[SessionManager] 销毁会话失败: {str(e)}", file=sys.stderr, flush=True)
                return False
    
    def handle_session(self, session_id):
        if session_id not in self.sessions:
            return
        
        session = self.sessions[session_id]
        request_pipe = session["request_pipe"]
        response_pipe = session["response_pipe"]
        history = self.session_histories.get(session_id)
        
        print(f"[SessionManager] 开始处理会话: {session_id}", file=sys.stderr, flush=True)
        
        try:
            with open(request_pipe, 'r') as req_pipe:
                while session["active"]:
                    try:
                        line = req_pipe.readline().strip()
                        if not line:
                            continue
                        
                        request = json.loads(line)
                        service_type = request.get('service', '')
                        
                        if service_type == 'asr':
                            audio_path = request.get('audio_path', '')
                            result = self.model_manager.recognize_audio(audio_path)
                        
                        elif service_type == 'llm':
                            prompt = request.get('prompt', '')
                            model = request.get('model', 'qwen3')
                            max_tokens = request.get('max_tokens', 1000)
                            temperature = request.get('temperature', 0.7)
                            result = self.model_manager.generate_text(prompt, model, max_tokens, temperature, session_id)
                        
                        elif service_type == 'chat':
                            user_input = request.get('prompt', '')
                            history_list = history.get_history() if history else None
                            result = self.model_manager.generate_chat_response(session_id, user_input, history_list)
                            
                            if result.get('success') and result.get('text') and not result.get('should_route'):
                                if history:
                                    history.add_turn('user', user_input)
                                    history.add_turn('assistant', result['text'])
                        
                        elif service_type == 'classify_intent':
                            text = request.get('text', '')
                            result = self.model_manager.classify_intent(text)
                            result['success'] = True
                        
                        elif service_type == 'tts':
                            text = request.get('text', '')
                            language = request.get('language', 'zh')
                            speed = request.get('speed', 1.0)
                            output_path = request.get('output_path', '')
                            result = self.model_manager.synthesize_speech(text, language, speed, output_path)
                        
                        elif service_type == 'speaker_register':
                            audio_path = request.get('audio_path', '')
                            speaker_id = request.get('speaker_id', '')
                            name = request.get('name', '')
                            profile = request.get('profile', {})
                            result = self.model_manager.register_speaker(audio_path, speaker_id, name, profile)
                        
                        elif service_type == 'speaker_verify':
                            audio_path = request.get('audio_path', '')
                            speaker_id = request.get('speaker_id')
                            threshold = request.get('threshold')
                            result = self.model_manager.verify_speaker(audio_path, speaker_id, threshold)
                        
                        elif service_type == 'speaker_list':
                            speakers = []
                            for sid, data in self.model_manager.speaker_embeddings.items():
                                speakers.append({
                                    'speaker_id': sid,
                                    'name': data.get('name', sid),
                                    'registered_at': data.get('registered_at', ''),
                                    'profile': data.get('profile', {})
                                })
                            result = {"success": True, "speakers": speakers, "count": len(speakers)}
                        
                        elif service_type == 'speaker_delete':
                            speaker_id = request.get('speaker_id', '')
                            if speaker_id in self.model_manager.speaker_embeddings:
                                del self.model_manager.speaker_embeddings[speaker_id]
                                self.model_manager._save_speaker_data()
                                result = {"success": True, "message": f"说话人 {speaker_id} 已删除"}
                            else:
                                result = {"success": False, "message": f"说话人 {speaker_id} 不存在"}
                        
                        elif service_type == 'status':
                            result = self.model_manager.get_status()
                            result["success"] = True
                            result["stats"] = self.model_manager.get_stats()
                        
                        elif service_type == 'clear_history':
                            if history:
                                history.clear()
                            result = {"success": True, "message": "History cleared"}
                        
                        else:
                            result = {"success": False, "error": f"未知服务: {service_type}"}
                        
                        with open(response_pipe, 'w') as resp_pipe:
                            resp_pipe.write(json.dumps(result, ensure_ascii=False) + '\n')
                            resp_pipe.flush()
                            
                    except json.JSONDecodeError as e:
                        result = {"success": False, "error": f"JSON解析失败: {str(e)}"}
                        with open(response_pipe, 'w') as resp_pipe:
                            resp_pipe.write(json.dumps(result, ensure_ascii=False) + '\n')
                            resp_pipe.flush()
                    except Exception as e:
                        result = {"success": False, "error": f"处理失败: {str(e)}"}
                        with open(response_pipe, 'w') as resp_pipe:
                            resp_pipe.write(json.dumps(result, ensure_ascii=False) + '\n')
                            resp_pipe.flush()
                        
        except Exception as e:
            print(f"[SessionManager] 会话异常: {session_id}, {str(e)}", file=sys.stderr, flush=True)
        finally:
            print(f"[SessionManager] 会话结束: {session_id}", file=sys.stderr, flush=True)
    
    def start_session_handler(self, session_id):
        self.executor.submit(self.handle_session, session_id)
    
    def get_session_count(self):
        with self.lock:
            return len([s for s in self.sessions.values() if s["active"]])
    
    def shutdown(self):
        with self.lock:
            session_ids = list(self.sessions.keys())
        for session_id in session_ids:
            self.destroy_session(session_id)
        self.executor.shutdown(wait=True)
        print("[SessionManager] 已关闭", file=sys.stderr, flush=True)


def start_speaker_http_server(model_manager, port=8391):
    """启动声纹识别HTTP服务"""
    from http.server import HTTPServer, BaseHTTPRequestHandler
    import urllib.parse
    
    class SpeakerHTTPHandler(BaseHTTPRequestHandler):
        def log_message(self, format, *args):
            pass
        
        def send_json_response(self, data, status=200):
            self.send_response(status)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(json.dumps(data, ensure_ascii=False).encode('utf-8'))
        
        def do_OPTIONS(self):
            self.send_response(200)
            self.send_header('Access-Control-Allow-Origin', '*')
            self.send_header('Access-Control-Allow-Methods', 'GET, POST, DELETE')
            self.send_header('Access-Control-Allow-Headers', 'Content-Type')
            self.end_headers()
        
        def do_GET(self):
            parsed = urllib.parse.urlparse(self.path)
            path = parsed.path
            
            if path == '/health':
                self.send_json_response({
                    "status": "healthy",
                    "cam_loaded": model_manager.models_loaded.get('cam', False),
                    "speakers_registered": len(model_manager.speaker_embeddings)
                })
            elif path == '/speakers':
                speakers = []
                for sid, data in model_manager.speaker_embeddings.items():
                    speakers.append({
                        'speaker_id': sid,
                        'name': data.get('name', sid),
                        'registered_at': data.get('registered_at', ''),
                        'profile': data.get('profile', {})
                    })
                self.send_json_response({"success": True, "speakers": speakers, "count": len(speakers)})
            else:
                self.send_json_response({"success": False, "message": "Not found"}, 404)
        
        def do_POST(self):
            parsed = urllib.parse.urlparse(self.path)
            path = parsed.path
            
            content_length = int(self.headers.get('Content-Length', 0))
            content_type = self.headers.get('Content-Type', '')
            
            try:
                if path == '/register':
                    if 'multipart/form-data' in content_type:
                        import cgi
                        form = cgi.FieldStorage(
                            fp=self.rfile,
                            headers=self.headers,
                            environ={'REQUEST_METHOD': 'POST'}
                        )
                        
                        speaker_id = form.getvalue('speaker_id', '')
                        name = form.getvalue('name', speaker_id)
                        audio_file = form['audio']
                        
                        import tempfile
                        with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp:
                            tmp.write(audio_file.file.read())
                            tmp_path = tmp.name
                        
                        profile_str = form.getvalue('profile', '{}')
                        try:
                            profile = json.loads(profile_str) if isinstance(profile_str, str) else {}
                        except:
                            profile = {}
                        
                        result = model_manager.register_speaker(tmp_path, speaker_id, name, profile)
                        os.unlink(tmp_path)
                    else:
                        body = self.rfile.read(content_length).decode('utf-8')
                        data = json.loads(body)
                        result = model_manager.register_speaker(
                            data.get('audio_path', ''),
                            data.get('speaker_id', ''),
                            data.get('name'),
                            data.get('profile')
                        )
                    self.send_json_response(result)
                
                elif path == '/verify':
                    if 'multipart/form-data' in content_type:
                        import cgi
                        form = cgi.FieldStorage(
                            fp=self.rfile,
                            headers=self.headers,
                            environ={'REQUEST_METHOD': 'POST'}
                        )
                        
                        speaker_id = form.getvalue('speaker_id')
                        threshold = form.getvalue('threshold')
                        threshold = float(threshold) if threshold else None
                        audio_file = form['audio']
                        
                        import tempfile
                        with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp:
                            tmp.write(audio_file.file.read())
                            tmp_path = tmp.name
                        
                        result = model_manager.verify_speaker(tmp_path, speaker_id, threshold)
                        os.unlink(tmp_path)
                    else:
                        body = self.rfile.read(content_length).decode('utf-8')
                        data = json.loads(body)
                        result = model_manager.verify_speaker(
                            data.get('audio_path', ''),
                            data.get('speaker_id'),
                            data.get('threshold')
                        )
                    self.send_json_response(result)
                
                elif path == '/identify':
                    if 'multipart/form-data' in content_type:
                        import cgi
                        form = cgi.FieldStorage(
                            fp=self.rfile,
                            headers=self.headers,
                            environ={'REQUEST_METHOD': 'POST'}
                        )
                        
                        threshold = form.getvalue('threshold')
                        threshold = float(threshold) if threshold else None
                        audio_file = form['audio']
                        
                        import tempfile
                        with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp:
                            tmp.write(audio_file.file.read())
                            tmp_path = tmp.name
                        
                        result = model_manager.verify_speaker(tmp_path, None, threshold)
                        os.unlink(tmp_path)
                    else:
                        body = self.rfile.read(content_length).decode('utf-8')
                        data = json.loads(body)
                        result = model_manager.verify_speaker(
                            data.get('audio_path', ''),
                            None,
                            data.get('threshold')
                        )
                    self.send_json_response(result)
                
                else:
                    self.send_json_response({"success": False, "message": "Not found"}, 404)
            
            except Exception as e:
                self.send_json_response({"success": False, "message": str(e)}, 500)
        
        def do_DELETE(self):
            parsed = urllib.parse.urlparse(self.path)
            path = parsed.path
            
            if path.startswith('/speakers/'):
                speaker_id = path[10:]
                if speaker_id in model_manager.speaker_embeddings:
                    del model_manager.speaker_embeddings[speaker_id]
                    model_manager._save_speaker_data()
                    self.send_json_response({"success": True, "message": f"说话人 {speaker_id} 已删除"})
                else:
                    self.send_json_response({"success": False, "message": f"说话人 {speaker_id} 不存在"}, 404)
            else:
                self.send_json_response({"success": False, "message": "Not found"}, 404)
    
    server = HTTPServer(('0.0.0.0', port), SpeakerHTTPHandler)
    print(f"[ModelDaemon] 🎤 声纹识别HTTP服务启动于端口 {port}", file=sys.stderr, flush=True)
    server.serve_forever()


def main():
    print("[ModelDaemon] 🎯 Living Agent 模型守护进程启动 (双模型架构)", file=sys.stderr, flush=True)
    print("[ModelDaemon] 📋 架构说明:", file=sys.stderr, flush=True)
    print("[ModelDaemon]   - Qwen3-0.6B: 沟通、表达、高效回复 (Layer 2)", file=sys.stderr, flush=True)
    print("[ModelDaemon]   - Qwen3.5-2B: 任务转达、工具调用、部门引导 (Layer 3)", file=sys.stderr, flush=True)
    print("[ModelDaemon]   - CAM++: 声纹识别、说话人验证 (Speaker)", file=sys.stderr, flush=True)
    
    manager = ModelManager()
    
    if not manager.load_all_models():
        print("[ModelDaemon] ❌ 没有成功加载任何模型，退出", file=sys.stderr, flush=True)
        sys.exit(1)
    
    session_manager = SessionManager(manager, max_workers=10)
    manager.session_manager = session_manager
    
    # 启动声纹识别HTTP服务
    speaker_http_port = int(os.environ.get('SPEAKER_HTTP_PORT', '8391'))
    speaker_http_thread = threading.Thread(
        target=start_speaker_http_server,
        args=(manager, speaker_http_port),
        name="SpeakerHTTPServer",
        daemon=True
    )
    speaker_http_thread.start()
    print(f"[ModelDaemon] 🎤 声纹识别HTTP服务启动于端口 {speaker_http_port}", file=sys.stderr, flush=True)
    
    print("[ModelDaemon] 🚀 守护进程就绪，等待请求...", file=sys.stderr, flush=True)
    
    control_request_pipe = "/tmp/dialogue_daemon_control_request"
    control_response_pipe = "/tmp/dialogue_daemon_control_response"
    
    for pipe in [control_request_pipe, control_response_pipe]:
        if os.path.exists(pipe):
            os.unlink(pipe)
        os.mkfifo(pipe, 0o666)
    
    print(f"[ModelDaemon] 创建控制管道: {control_request_pipe}", file=sys.stderr, flush=True)
    
    try:
        while True:
            try:
                with open(control_request_pipe, 'r') as req_pipe:
                    line = req_pipe.readline().strip()
                    if not line:
                        continue
                    
                    request = json.loads(line)
                    action = request.get('action', '')
                    
                    if action == 'create_session':
                        session_id = request.get('session_id', '')
                        if session_id:
                            success = session_manager.create_session(session_id)
                            if success:
                                session_manager.start_session_handler(session_id)
                            result = {"success": success, "session_id": session_id}
                        else:
                            result = {"success": False, "error": "缺少session_id"}
                    
                    elif action == 'destroy_session':
                        session_id = request.get('session_id', '')
                        if session_id:
                            success = session_manager.destroy_session(session_id)
                            result = {"success": success, "session_id": session_id}
                        else:
                            result = {"success": False, "error": "缺少session_id"}
                    
                    elif action == 'get_status':
                        result = {
                            "success": True,
                            "model_status": manager.get_status(),
                            "session_count": session_manager.get_session_count(),
                            "stats": manager.get_stats()
                        }
                    
                    elif action == 'shutdown':
                        session_manager.shutdown()
                        result = {"success": True, "message": "守护进程已关闭"}
                        break
                    
                    else:
                        result = {"success": False, "error": f"未知操作: {action}"}
                    
                    with open(control_response_pipe, 'w') as resp_pipe:
                        resp_pipe.write(json.dumps(result, ensure_ascii=False) + '\n')
                        resp_pipe.flush()
                        
            except json.JSONDecodeError as e:
                result = {"success": False, "error": f"JSON解析失败: {str(e)}"}
                with open(control_response_pipe, 'w') as resp_pipe:
                    resp_pipe.write(json.dumps(result, ensure_ascii=False) + '\n')
                    resp_pipe.flush()
            except Exception as e:
                result = {"success": False, "error": f"处理失败: {str(e)}"}
                with open(control_response_pipe, 'w') as resp_pipe:
                    resp_pipe.write(json.dumps(result, ensure_ascii=False) + '\n')
                    resp_pipe.flush()
                
    except KeyboardInterrupt:
        print("\n[ModelDaemon] 收到停止信号", file=sys.stderr, flush=True)
    finally:
        session_manager.shutdown()
        for pipe in [control_request_pipe, control_response_pipe]:
            if os.path.exists(pipe):
                os.unlink(pipe)
        print("[ModelDaemon] 守护进程已退出", file=sys.stderr, flush=True)


if __name__ == "__main__":
    main()
