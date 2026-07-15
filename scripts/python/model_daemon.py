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
import errno
import subprocess
import hashlib
import time
import json
import contextlib
import traceback
import threading
from collections import deque
from pathlib import Path
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
# MeloTTS 支持的语言列表，可按需裁剪以节省内存 (默认全部加载)
# 例如内存不足时设置 MELOTTS_LANGUAGES=zh,en,fr,es,kr 跳过 jp
MELOTTS_LANGUAGES = os.environ.get('MELOTTS_LANGUAGES', 'zh,en,fr,es,jp,kr').split(',')
CAM_MODEL_DIR = os.environ.get('CAM_MODEL_DIR',
    '/app/ai-models/cam')
SPEAKER_DATA_FILE = os.environ.get('SPEAKER_DATA_FILE',
    '/app/data/speaker_embeddings.json')
SPEAKER_THRESHOLD = float(os.environ.get('SPEAKER_THRESHOLD', '0.33'))
# 远程声纹服务配置
SPEAKER_USE_REMOTE = os.environ.get('SPEAKER_USE_REMOTE', 'false').lower() == 'true'
SPEAKER_SERVICE_URL = os.environ.get('SPEAKER_SERVICE_URL', 'http://host.docker.internal:8390')

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
                    if QuickGreetingGenerator._is_pure_greeting(lower, g):
                        return "早上好！今天有什么我可以帮助您的吗？"
        
        for g in GREETINGS['afternoon']:
            if g in lower:
                if QuickGreetingGenerator._is_pure_greeting(lower, g):
                    return "下午好！有什么我可以为您做的吗？"
        
        for g in GREETINGS['evening']:
            if g in lower:
                if QuickGreetingGenerator._is_pure_greeting(lower, g):
                    if '晚安' in lower:
                        return "晚安！祝您有个好梦。"
                    return "晚上好！有什么我可以帮您的吗？"
        
        if re.match(r'.*在[吗呢].*', lower) or '在不在' in lower or '有人吗' in lower:
            if QuickGreetingGenerator._is_pure_greeting(lower, None):
                return "我在的，有什么可以帮您？"
        
        for g in GREETINGS['general']:
            if g in lower:
                if QuickGreetingGenerator._is_pure_greeting(lower, g):
                    return "您好！我是公司的前台助手，有什么可以帮您的吗？"
        
        return None
    
    @staticmethod
    def _is_pure_greeting(lower: str, greeting: Optional[str] = None) -> bool:
        """判断是否为纯问候（无后续问题/请求）
        
        如果是纯问候词（如"你好"），返回 True
        如果问候词后还有其他内容（如"你好，你是什么模型"），返回 False
        """
        text = lower.strip()
        
        question_patterns = [
            r'[吗呢啊呀]+$',
            r'什么',
            r'怎么',
            r'如何',
            r'为什么',
            r'哪个',
            r'多少',
            r'能[不否]',
            r'可以[不否]',
            r'有没有',
            r'是不是',
            r'会不会',
        ]
        
        for pattern in question_patterns:
            if re.search(pattern, text):
                return False
        
        if text.count('，') > 0 or text.count(',') > 0:
            return False
        if text.count('？') > 0 or text.count('?') > 0:
            return False
        
        return True

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
        llama_server_path = os.path.join(llama_cpp_path, 'build', 'bin', 'llama-server')
        
        # 优先使用llama-server（独立进程，稳定），回退到llama-cli
        if os.path.exists(llama_server_path):
            self.llama_server_path = llama_server_path
            self.llama_cli_path = None
            print(f"[ModelDaemon] 使用 llama.cpp server: {llama_server_path}", file=sys.stderr, flush=True)
        elif os.path.exists(llama_cli_path):
            self.llama_server_path = None
            self.llama_cli_path = llama_cli_path
            print(f"[ModelDaemon] 使用 llama.cpp CLI: {llama_cli_path}", file=sys.stderr, flush=True)
        else:
            self.llama_server_path = None
            self.llama_cli_path = None
            print("[ModelDaemon] ❌ llama.cpp server/CLI 均未找到", file=sys.stderr, flush=True)
        
        if os.path.exists(QWEN3_MODEL_FILE):
            print(f"[ModelDaemon] ✅ Qwen3-0.6B 模型文件存在: {QWEN3_MODEL_FILE}", file=sys.stderr, flush=True)
        
        if os.path.exists(QWEN35_MODEL_FILE):
            print(f"[ModelDaemon] ✅ Qwen3.5-2B 模型文件存在: {QWEN35_MODEL_FILE}", file=sys.stderr, flush=True)
        
        # 使用llama-server时，启动独立服务进程
        if self.llama_server_path:
            self._start_llama_servers()
            if os.path.exists(QWEN3_MODEL_FILE):
                self.models_loaded['qwen3'] = True
                print("[ModelDaemon] ✅ Qwen3-0.6B 将通过 llama-server 使用 (沟通模型)", file=sys.stderr, flush=True)
            if os.path.exists(QWEN35_MODEL_FILE):
                self.models_loaded['qwen35'] = True
                print("[ModelDaemon] ✅ Qwen3.5-2B 将通过 llama-server 使用 (任务转达模型)", file=sys.stderr, flush=True)
        elif self.llama_cli_path and os.path.exists(self.llama_cli_path):
            if os.path.exists(QWEN3_MODEL_FILE):
                self.models_loaded['qwen3'] = True
                print("[ModelDaemon] ✅ Qwen3-0.6B 将通过 CLI 使用 (沟通模型)", file=sys.stderr, flush=True)
            if os.path.exists(QWEN35_MODEL_FILE):
                self.models_loaded['qwen35'] = True
                print("[ModelDaemon] ✅ Qwen3.5-2B 将通过 CLI 使用 (任务转达模型)", file=sys.stderr, flush=True)
        else:
            print("[ModelDaemon] ❌ llama.cpp 不可用，LLM 模型无法加载", file=sys.stderr, flush=True)
    
    def _start_llama_servers(self):
        """启动llama-server进程（每个模型一个）"""
        self.llama_server_procs = {}
        self.llama_server_ports = {}
        
        base_port = int(os.environ.get('LLAMA_SERVER_BASE_PORT', '8393'))
        
        models_to_start = []
        if os.path.exists(QWEN3_MODEL_FILE):
            models_to_start.append(('qwen3', QWEN3_MODEL_FILE, 512))  # 闲聊：小上下文，快速响应
        if os.path.exists(QWEN35_MODEL_FILE):
            models_to_start.append(('qwen35', QWEN35_MODEL_FILE, 4096))  # 工具路由：中等上下文，平衡速度
        
        for i, (model_key, model_path, ctx_size) in enumerate(models_to_start):
            port = base_port + i
            try:
                proc = subprocess.Popen(
                    [
                        self.llama_server_path,
                        '-m', model_path,
                        '--ctx-size', str(ctx_size),
                        '--port', str(port),
                        '--host', '127.0.0.1',
                        '-t', '2',
                        '-np', '1',
                        '--metrics'
                    ],
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.PIPE,
                    preexec_fn=os.setsid if hasattr(os, 'setsid') else None
                )
                self.llama_server_procs[model_key] = proc
                self.llama_server_ports[model_key] = port
                print(f"[ModelDaemon] ✅ llama-server 启动: model={model_key}, port={port}, pid={proc.pid}", file=sys.stderr, flush=True)
            except Exception as e:
                print(f"[ModelDaemon] ❌ llama-server 启动失败: model={model_key}, error={str(e)}", file=sys.stderr, flush=True)
        
        # 等待服务器就绪
        import urllib.request
        max_wait = 30
        for model_key, port in self.llama_server_ports.items():
            for attempt in range(max_wait):
                try:
                    req = urllib.request.Request(f'http://127.0.0.1:{port}/health')
                    with urllib.request.urlopen(req, timeout=2) as resp:
                        if resp.status == 200:
                            print(f"[ModelDaemon] ✅ llama-server 就绪: model={model_key}, port={port}", file=sys.stderr, flush=True)
                            break
                except Exception:
                    time.sleep(1)
            else:
                print(f"[ModelDaemon] ⚠️ llama-server 就绪超时: model={model_key}, port={port}", file=sys.stderr, flush=True)
    
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
            
            supported_languages = [lang.strip() for lang in MELOTTS_LANGUAGES if lang.strip()]
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
        # 如果配置了远程声纹服务，跳过本地模型加载
        if SPEAKER_USE_REMOTE:
            print(f"[ModelDaemon] 🎤 使用远程声纹服务: {SPEAKER_SERVICE_URL}", file=sys.stderr, flush=True)
            self.speaker_service_url = SPEAKER_SERVICE_URL
            self.models_loaded['cam'] = True  # 标记为已加载（实际使用远程服务）
            self._load_speaker_data()
            return
        
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
        # 远程模式：调用HTTP API
        if SPEAKER_USE_REMOTE:
            return self._extract_speaker_embedding_remote(audio_path)
        
        # 本地模式：使用CAM模型
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
    
    def _extract_speaker_embedding_remote(self, audio_path):
        """远程调用声纹提取API"""
        try:
            import urllib.request
            import urllib.error
            import random
            import string
            
            url = f"{self.speaker_service_url}/extract"
            
            # 读取音频文件
            with open(audio_path, 'rb') as f:
                audio_data = f.read()
            
            boundary = '----WebKitFormBoundary' + ''.join(random.choices(string.ascii_letters + string.digits, k=16))
            body = f'--{boundary}\r\n'
            body += f'Content-Disposition: form-data; name="audio"; filename="audio.wav"\r\n'
            body += 'Content-Type: audio/wav\r\n\r\n'
            body_encode = body.encode('utf-8')
            end_boundary = f'\r\n--{boundary}--\r\n'.encode('utf-8')
            
            req = urllib.request.Request(
                url,
                data=body_encode + audio_data + end_boundary,
                headers={'Content-Type': f'multipart/form-data; boundary={boundary}'},
                method='POST'
            )
            
            with urllib.request.urlopen(req, timeout=30) as response:
                result = json.loads(response.read().decode('utf-8'))
            
            if result.get('success') and 'embedding' in result:
                return np.array(result['embedding'], dtype=np.float32).flatten()
        except Exception as e:
            print(f"[ModelDaemon] ❌ 远程声纹提取失败: {str(e)}", file=sys.stderr, flush=True)
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
    
    # ===== 公共工具定义（与公司内部工具独立，不混合） =====
    PUBLIC_TOOL_DEFINITIONS = [
        {
            "name": "weather_query",
            "description": "查询指定城市的当前天气情况，包括温度、湿度、天气状况等",
            "parameters": {
                "type": "object",
                "properties": {
                    "location": {
                        "type": "string",
                        "description": "城市名称，如'广州'、'北京'、'上海'"
                    }
                },
                "required": ["location"]
            }
        },
        {
            "name": "time_query",
            "description": "获取当前时间或指定时区的时间信息",
            "parameters": {
                "type": "object",
                "properties": {
                    "timezone": {
                        "type": "string",
                        "description": "时区名称，如'Asia/Shanghai'、'UTC'。不指定则返回本地时间"
                    }
                },
                "required": []
            }
        },
        {
            "name": "calculator",
            "description": "数学计算器，支持加减乘除、幂运算、括号等",
            "parameters": {
                "type": "object",
                "properties": {
                    "expression": {
                        "type": "string",
                        "description": "数学表达式，如'(3+5)*2'、'100/4'"
                    }
                },
                "required": ["expression"]
            }
        },
        {
            "name": "translation",
            "description": "中英文翻译服务",
            "parameters": {
                "type": "object",
                "properties": {
                    "text": {
                        "type": "string",
                        "description": "需要翻译的文本"
                    },
                    "direction": {
                        "type": "string",
                        "description": "翻译方向：'zh2en'(中译英)或'en2zh'(英译中)"
                    }
                },
                "required": ["text", "direction"]
            }
        },
        {
            "name": "encyclopedia",
            "description": "常识性问题百科解答，如历史、地理、科学等基础知识",
            "parameters": {
                "type": "object",
                "properties": {
                    "question": {
                        "type": "string",
                        "description": "需要解答的常识性问题"
                    }
                },
                "required": ["question"]
            }
        }
    ]

    def _build_tool_system_prompt(self) -> str:
        """构建智能前台工具提示词 - Qwen3.5-2B

        注意：这是"智能前台"的工具路由，只处理公共工具，不涉及公司内部管理。
        公司内部部门路由由Java端的ToolNeuron处理（需要登录认证）。
        公共工具和公司内部工具是完全独立的，不能混合。
        """
        # 构建工具定义的简化格式（小模型更适合简洁描述而非完整JSON Schema）
        tool_descs = []
        for t in self.PUBLIC_TOOL_DEFINITIONS:
            params_desc = ', '.join(
                p["description"] for p in t["parameters"].get("properties", {}).values()
            )
            req_params = ', '.join(t["parameters"].get("required", []))
            tool_descs.append('- ' + t["name"] + '(' + req_params + '): ' + t["description"])
        tools_text = '\n'.join(tool_descs)

        # 所有含花括号{}的JSON内容必须用变量拼接，不能放在f-string中
        tool_call_fmt = '{"tool_call": true, "tool": "TOOL_NAME", "parameters": PARAMS}'
        weather_example = '{"tool_call": true, "tool": "weather_query", "parameters": {"location": "广州"}}'
        time_example = '{"tool_call": true, "tool": "time_query", "parameters": {"timezone": "Asia/Shanghai"}}'
        calc_example = '{"tool_call": true, "tool": "calculator", "parameters": {"expression": "100/4"}}'
        no_tool_example = '您好！我可以为您提供公共信息服务。请问有什么需要帮助的？'

        prompt = (
            "你是公司的智能前台助手，负责接待访客并提供公共服务。\n\n"
            "公共工具（所有访客可用，与公司内部工具独立）：\n"
            + tools_text + "\n\n"
            "输出规则（严格遵守）：\n"
            "- 当用户需要使用工具时，你必须输出以下JSON格式，不能输出任何其他文字：\n"
            "  " + tool_call_fmt + "\n"
            "- 当不需要工具时，直接输出中文文本回复，不能输出JSON：\n"
            "  " + no_tool_example + "\n\n"
            "示例：\n"
            "用户：广州天气怎么样\n"
            "助手：" + weather_example + "\n\n"
            "用户：现在几点了\n"
            "助手：" + time_example + "\n\n"
            "用户：计算100除以4\n"
            "助手：" + calc_example + "\n\n"
            "用户：你好\n"
            "助手：您好！我是公司的智能前台助手，有什么可以帮您的吗？\n\n"
            "用户：公司有多少员工\n"
            "助手：关于公司内部信息，需要登录后联系相关部门。我目前只提供公共服务，如天气查询、时间查询、翻译、计算等。\n\n"
            "绝对禁止：\n"
            "- 输出思考过程或分析步骤\n"
            "- 输出包含\"Thinking Process\"的文本\n"
            "- 混合JSON和文字\n"
            "- 提供公司内部信息\n\n"
            "/no_think"
        )

        return prompt
    
    def _filter_thinking_process(self, text: str) -> str:
        """过滤Qwen3思考模式输出的思考过程，只保留最终回复
        
        Qwen3在思考模式下可能输出：
        - "Thinking Process: ..." 开头的完整思考过程
        - "<think>...</think>" 标签包裹的思考内容
        - 多行思考后才有实际回复
        
        此方法提取思考过程后面的实际助手回复。
        """
        if not text:
            return text
        
        # 检测常见思考过程标记
        thinking_markers = [
            "Thinking Process:",
            "Think Process:",
            "<think>",
            "## Thinking",
            "**Thinking**:",
        ]
        
        # 尝试提取思考过程后的实际内容
        for marker in thinking_markers:
            if marker in text:
                # 找到标记位置
                marker_pos = text.find(marker)
                
                # 尝试找思考过程结束的位置
                # Qwen3的思考过程通常以双换行+实际回复开头结束
                # 或者以</think>标签结束
                
                after_marker = text[marker_pos + len(marker):]
                
                # 检查是否有</think>结束标签
                end_tag = "</think>"
                if end_tag in after_marker:
                    end_pos = after_marker.find(end_tag) + len(end_tag)
                    actual_response = after_marker[end_pos:].strip()
                    if actual_response:
                        return actual_response
                
                # 尝试通过模式匹配提取最终回复
                # 思考过程后面通常有明显的分隔（如多个换行后跟着中文回复）
                lines = after_marker.split('\n')
                
                # 找到第一个非空行开始的回复
                # 跳过思考过程的各行（通常以空行、数字列表等开头）
                in_thinking = True
                response_lines = []
                
                for i, line in enumerate(lines):
                    stripped = line.strip()
                    
                    # 检测思考过程结束的标志
                    # 1. 连续空行后的非空行
                    # 2. 以中文开头的行（实际回复通常直接是中文句子）
                    # 3. "Assistant:" 或 "助手：" 开头的行
                    
                    if in_thinking:
                        # 跳过空行
                        if not stripped:
                            continue
                        
                        # 检测是否是思考过程的格式（通常以数字、点、横线开头）
                        if stripped.startswith(('1.', '2.', '3.', '-', '*', '•', '>', '分析', 'Determine', 'Draft', 'Plan', 'Wait', 'Refined', 'Actually')):
                            continue
                        
                        # 检测是否是实际的回复开头（中文或英文的正常句子）
                        if stripped and (
                            stripped[0].encode('utf-8')[0] > 127 or  # 中文字符
                            stripped.startswith(('您好', '你好', 'Hello', 'Hi', 'I ', 'We ', 'Yes', 'No')) or
                            ':' in stripped[:10]  # "Assistant:" 格式
                        ):
                            in_thinking = False
                            response_lines.append(line)
                    else:
                        response_lines.append(line)
                
                if response_lines:
                    return '\n'.join(response_lines).strip()
        
        # 策略4：使用正则表达式提取中文问候语开头的回复
        import re
        
        # 匹配引号内的中文回复
        quoted_match = re.search(r'"(您好[^"]+)"', text)
        if quoted_match:
            return quoted_match.group(1).strip()
        
        # 匹配中文问候语开头的回复
        greeting_match = re.search(r'(您好[！？。，].*?)(?:\n\n|\Z)', text, re.DOTALL)
        if greeting_match:
            return greeting_match.group(1).strip()
        
        greeting_match = re.search(r'(我是您的智能前台助手[^。\n]*[。？！])', text)
        if greeting_match:
            return greeting_match.group(1).strip()
        
        greeting_match = re.search(r'(请问您想[^\n]*[？？])', text)
        if greeting_match:
            return greeting_match.group(1).strip()
        
        # 如果没有找到思考标记，直接返回原文
        return text.strip()

    def _parse_tool_call(self, text: str) -> Optional[Dict]:
        """从模型输出中解析工具调用JSON

        模型可能输出以下格式之一：
        1. 纯JSON: {"tool_call": true, "tool": "weather_query", "parameters": {"location": "广州"}}
        2. JSON嵌入在文本中
        3. 混合思考过程+JSON

        Returns:
            如果解析成功，返回 {"tool_call": True, "tool": str, "parameters": dict}
            如果不是工具调用，返回 None
        """
        if not text:
            return None

        # 先过滤思考过程
        cleaned = self._filter_thinking_process(text)

        # 策略1: 直接尝试解析整个清理后的文本为JSON
        for candidate in [cleaned, text]:
            candidate = candidate.strip()
            # 移除可能的markdown代码块标记
            if candidate.startswith('```'):
                candidate = re.sub(r'^```(?:json)?\s*\n?', '', candidate)
                candidate = re.sub(r'\n?```$', '', candidate)
                candidate = candidate.strip()

            try:
                parsed = json.loads(candidate)
                if isinstance(parsed, dict) and parsed.get('tool_call') is True:
                    tool_name = parsed.get('tool')
                    parameters = parsed.get('parameters', {})
                    if tool_name and isinstance(tool_name, str):
                        return {"tool_call": True, "tool": tool_name, "parameters": parameters}
            except (json.JSONDecodeError, ValueError):
                pass

        # 策略2: 在文本中搜索JSON块（可能被其他文字包裹）
        json_patterns = [
            r'\{[^{}]*"tool_call"\s*:\s*true[^{}]*\}',  # 单层JSON
            r'\{[^{}]*"tool_call"\s*:\s*True[^{}]*\}',   # Python风格布尔
        ]
        for pattern in json_patterns:
            matches = re.finditer(pattern, text, re.DOTALL)
            for match in matches:
                json_str = match.group()
                # 修复Python风格的True为JSON的true
                json_str = json_str.replace('True', 'true').replace('False', 'false')
                try:
                    parsed = json.loads(json_str)
                    if isinstance(parsed, dict) and parsed.get('tool_call') is True:
                        tool_name = parsed.get('tool')
                        parameters = parsed.get('parameters', {})
                        if tool_name and isinstance(tool_name, str):
                            return {"tool_call": True, "tool": tool_name, "parameters": parameters}
                except (json.JSONDecodeError, ValueError):
                    pass

        # 策略3: 更宽松的搜索 - 找包含 "tool" 和 "parameters" 键的JSON对象
        json_block_pattern = r'\{(?:[^{}]|(?:\{[^{}]*\}))*\}'
        for match in re.finditer(json_block_pattern, text, re.DOTALL):
            json_str = match.group()
            try:
                parsed = json.loads(json_str)
                if isinstance(parsed, dict):
                    # 检查是否有工具调用的关键字段
                    tool_name = parsed.get('tool') or parsed.get('name') or parsed.get('function')
                    params = parsed.get('parameters') or parsed.get('args') or parsed.get('arguments')
                    if tool_name and isinstance(tool_name, str):
                        # 验证是否是已知公共工具
                        known_tools = [t['name'] for t in self.PUBLIC_TOOL_DEFINITIONS]
                        if tool_name in known_tools:
                            return {"tool_call": True, "tool": tool_name, "parameters": params or {}}
            except (json.JSONDecodeError, ValueError):
                pass

        return None

    def _execute_public_tool(self, tool_name: str, parameters: Dict) -> Dict:
        """执行公共工具并返回结果

        公共工具是完全独立的，不涉及公司内部工具（gitlab/jenkins等）。
        公共工具对所有访客可用，无需认证。

        Args:
            tool_name: 工具名称
            parameters: 工具参数

        Returns:
            {"success": bool, "result": str, "tool": str, "parameters": dict}
        """
        import datetime
        try:
            if tool_name == 'weather_query':
                location = parameters.get('location', '')
                if not location:
                    return {"success": False, "result": "请提供城市名称", "tool": tool_name, "parameters": parameters}
                # 使用wttr.in免费天气API（无需密钥，公开服务）
                weather_result = self._query_weather(location)
                return {"success": weather_result.get('success', False),
                        "result": weather_result.get('result', '天气查询失败'),
                        "tool": tool_name, "parameters": parameters}

            elif tool_name == 'time_query':
                timezone = parameters.get('timezone', 'Asia/Shanghai')
                now = datetime.datetime.now(tz=datetime.timezone.utc)
                try:
                    import zoneinfo
                    tz = zoneinfo.ZoneInfo(timezone)
                    local_time = now.astimezone(tz)
                    result = f"当前时间({timezone}): {local_time.strftime('%Y年%m月%d日 %H:%M:%S')}"
                except Exception:
                    # 回退：使用UTC偏移估算
                    result = f"当前UTC时间: {now.strftime('%Y年%m月%d日 %H:%M:%S')}"
                return {"success": True, "result": result, "tool": tool_name, "parameters": parameters}

            elif tool_name == 'calculator':
                expression = parameters.get('expression', '')
                if not expression:
                    return {"success": False, "result": "请提供数学表达式", "tool": tool_name, "parameters": parameters}
                # 安全计算：只允许数字和基本运算符
                safe_expr = re.sub(r'[^\d+\-*/().%\s]', '', expression)
                if not safe_expr:
                    return {"success": False, "result": "表达式无效", "tool": tool_name, "parameters": parameters}
                try:
                    calc_result = eval(safe_expr, {"__builtins__": {}}, {})
                    return {"success": True, "result": f"{expression} = {calc_result}",
                            "tool": tool_name, "parameters": parameters}
                except Exception as e:
                    return {"success": False, "result": f"计算错误: {str(e)}",
                            "tool": tool_name, "parameters": parameters}

            elif tool_name == 'translation':
                text_to_translate = parameters.get('text', '')
                direction = parameters.get('direction', 'zh2en')
                if not text_to_translate:
                    return {"success": False, "result": "请提供需要翻译的文本",
                            "tool": tool_name, "parameters": parameters}
                # 使用模型自身进行翻译（无需外部API）
                if direction == 'zh2en':
                    trans_prompt = f"请将以下中文翻译为英文，只输出翻译结果：\n{text_to_translate}"
                else:
                    trans_prompt = f"请将以下英文翻译为中文，只输出翻译结果：\n{text_to_translate}"
                trans_result = self.generate_text(trans_prompt, model='qwen3',
                                                  max_tokens=256, temperature=0.3)
                if trans_result.get('success'):
                    translated = self._filter_thinking_process(trans_result.get('text', ''))
                    return {"success": True, "result": translated,
                            "tool": tool_name, "parameters": parameters}
                else:
                    return {"success": False, "result": "翻译失败",
                            "tool": tool_name, "parameters": parameters}

            elif tool_name == 'encyclopedia':
                question = parameters.get('question', '')
                if not question:
                    return {"success": False, "result": "请提供问题",
                            "tool": tool_name, "parameters": parameters}
                # 使用模型自身的知识回答百科问题
                enc_prompt = f"请简明回答以下常识性问题，只输出答案：\n{question}"
                enc_result = self.generate_text(enc_prompt, model='qwen3',
                                                 max_tokens=256, temperature=0.5)
                if enc_result.get('success'):
                    answer = self._filter_thinking_process(enc_result.get('text', ''))
                    return {"success": True, "result": answer,
                            "tool": tool_name, "parameters": parameters}
                else:
                    return {"success": False, "result": "百科查询失败",
                            "tool": tool_name, "parameters": parameters}

            else:
                return {"success": False, "result": f"未知公共工具: {tool_name}",
                        "tool": tool_name, "parameters": parameters}

        except Exception as e:
            return {"success": False, "result": f"工具执行异常: {str(e)}",
                    "tool": tool_name, "parameters": parameters}

    def _query_weather(self, location: str) -> Dict:
        """通过wttr.in免费API查询天气

        wttr.in 是公开的免费天气服务，无需API密钥。
        """
        import urllib.request
        import urllib.error

        try:
            # 使用wttr.in的JSON格式API
            encoded_location = urllib.request.quote(location)
            url = f'https://wttr.in/{encoded_location}?format=j1&lang=zh'

            req = urllib.request.Request(url, headers={
                'User-Agent': 'LivingAgentService/1.0',
                'Accept-Language': 'zh-CN,zh;q=0.9'
            })

            with urllib.request.urlopen(req, timeout=10) as response:
                data = json.loads(response.read().decode('utf-8'))

            current = data.get('current_condition', [{}])[0]
            temp_c = current.get('temp_C', 'N/A')
            humidity = current.get('humidity', 'N/A')
            desc = current.get('lang_zh', [{}])[0].get('value', '') if current.get('lang_zh') else current.get('weatherDesc', [{}])[0].get('value', '')
            wind_speed = current.get('windspeedKmph', 'N/A')
            feels_like = current.get('FeelsLikeC', 'N/A')

            # 找到城市名（中文优先）
            area = data.get('nearest_area', [{}])[0]
            city_name = area.get('areaName', [{}])[0].get('value', location)

            result_text = (
                f"{city_name}当前天气：{desc}，"
                f"温度{temp_c}°C（体感{feels_like}°C），"
                f"湿度{humidity}%，"
                f"风速{wind_speed}km/h"
            )
            return {"success": True, "result": result_text}

        except urllib.error.URLError as e:
            # 网络不可用时，尝试回退方案
            print(f"[ModelDaemon] 天气API不可用: {e}", file=sys.stderr, flush=True)
            return {"success": False, "result": f"天气查询暂时不可用（网络问题），请稍后再试"}
        except Exception as e:
            print(f"[ModelDaemon] 天气查询异常: {e}", file=sys.stderr, flush=True)
            return {"success": False, "result": f"天气查询失败"}

    def generate_text(self, prompt, model='qwen3', max_tokens=1000, temperature=0.7, session_id=None):
        start_time = time.time()
        
        with self.stats_lock:
            self.stats['total_requests'] += 1
        
        # 优先使用llama-server（独立进程，端口8393+）
        if hasattr(self, 'llama_server_ports') and self.llama_server_ports:
            return self._generate_text_via_server(prompt, model, max_tokens, temperature, start_time)
        
        # 回退：llama-cli CLI（可能崩溃）
        return self._generate_text_via_cli(prompt, model, max_tokens, temperature, start_time)
    
    def _generate_text_via_server(self, prompt, model, max_tokens, temperature, start_time):
        """通过llama-server HTTP API调用（推荐，稳定）"""
        import urllib.request
        import urllib.error
        
        model_key = model if model in self.llama_server_ports else 'qwen3'
        port = self.llama_server_ports.get(model_key, 8393)
        
        try:
            url = f'http://127.0.0.1:{port}/v1/chat/completions'
            data = json.dumps({
                "messages": [{"role": "user", "content": prompt}],
                "max_tokens": max_tokens,
                "temperature": temperature
            }).encode('utf-8')
            
            req = urllib.request.Request(
                url,
                data=data,
                headers={'Content-Type': 'application/json'},
                method='POST'
            )
            
            with urllib.request.urlopen(req, timeout=120) as response:
                result_json = json.loads(response.read().decode('utf-8'))
            
            if result_json.get('choices') and len(result_json['choices']) > 0:
                choice = result_json['choices'][0]
                msg = choice.get('message', {})
                response_text = msg.get('content', '')
                # Qwen3思考模式：如果content为空但有reasoning_content，取reasoning_content
                if not response_text and msg.get('reasoning_content'):
                    response_text = msg.get('reasoning_content', '')
                
                # 过滤Qwen3思考模式输出的思考过程（只保留最终回复）
                response_text = self._filter_thinking_process(response_text)
                
                latency = int((time.time() - start_time) * 1000)
                with self.stats_lock:
                    self.stats['total_latency_ms'] += latency
                    if model_key == 'qwen3':
                        self.stats['chat_model_calls'] += 1
                        self.stats['chat_latency_ms'] += latency
                    else:
                        self.stats['tool_model_calls'] += 1
                        self.stats['tool_latency_ms'] += latency
                
                return {"success": True, "text": response_text, "model": model_key, "backend": "llama-server", "latency_ms": latency}
            else:
                return {"success": False, "error": f"llama-server返回格式错误: {result_json}"}
                
        except urllib.error.URLError as e:
            return {"success": False, "error": f"llama-server连接失败: {str(e)}"}
        except Exception as e:
            return {"success": False, "error": f"LLM失败: {str(e)}"}
    
    def _generate_text_via_cli(self, prompt, model, max_tokens, temperature, start_time):
        """通过llama-cli调用（回退方案，可能崩溃）"""
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
                    
                    # 过滤Qwen3思考模式输出的思考过程
                    response = self._filter_thinking_process(response)
                    
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
                # 工具路由意图：调用工具模型(qwen35)处理
                # 使用多轮对话格式发送，以更好地支持结构化工具调用
                system_prompt = self._build_tool_system_prompt()

                result = self._generate_tool_call_response(
                    system_prompt, user_input, history, session_id
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

    def _generate_tool_call_response(self, system_prompt: str, user_input: str,
                                      history: List[Dict] = None, session_id: str = None) -> Dict:
        """生成工具调用响应：检测工具调用 → 执行 → 返回结果

        流程：
        1. 调用Qwen3.5-2B模型生成响应
        2. 解析模型输出是否包含tool_call JSON
        3. 如果是工具调用 → 执行公共工具 → 将结果格式化为回复
        4. 如果不是工具调用 → 返回模型文本回复
        """
        start_time = time.time()

        # 使用llama-server的chat格式（支持system+user多消息）
        if hasattr(self, 'llama_server_ports') and self.llama_server_ports:
            result = self._generate_tool_via_server(system_prompt, user_input, history, session_id)
        else:
            # 回退：使用拼接prompt方式
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

        if not result.get('success'):
            return result

        model_output = result.get('text', '')

        # 解析是否为工具调用
        tool_call_parsed = self._parse_tool_call(model_output)

        if tool_call_parsed and tool_call_parsed.get('tool_call'):
            # 工具调用 → 执行公共工具
            tool_name = tool_call_parsed['tool']
            tool_params = tool_call_parsed['parameters']
            print(f"[ModelDaemon] 检测到工具调用: tool={tool_name}, params={tool_params}",
                  file=sys.stderr, flush=True)

            tool_result = self._execute_public_tool(tool_name, tool_params)

            if tool_result.get('success'):
                # 工具执行成功 → 格式化结果为自然语言回复
                tool_reply = self._format_tool_result_as_reply(tool_name, tool_params, tool_result)
                latency = int((time.time() - start_time) * 1000)
                return {
                    "success": True,
                    "text": tool_reply,
                    "model": "tool-neuron+public-tool",
                    "tool_call": True,
                    "tool": tool_name,
                    "parameters": tool_params,
                    "tool_result": tool_result.get('result', ''),
                    "intent": "tool_call",
                    "confidence": 0.9,
                    "latency_ms": latency
                }
            else:
                # 工具执行失败 → 提示用户
                latency = int((time.time() - start_time) * 1000)
                return {
                    "success": True,
                    "text": f"抱歉，{tool_result.get('result', '工具执行失败')}。请稍后再试。",
                    "model": "tool-neuron",
                    "tool_call": True,
                    "tool": tool_name,
                    "parameters": tool_params,
                    "tool_result": tool_result.get('result', ''),
                    "tool_success": False,
                    "intent": "tool_call",
                    "confidence": 0.9,
                    "latency_ms": latency
                }
        else:
            # 不是工具调用 → 返回模型文本回复（过滤思考过程）
            cleaned_text = self._filter_thinking_process(model_output)
            result['text'] = cleaned_text
            # 标记这不是工具调用，便于Java端识别
            result['tool_call'] = False
            return result

    def _generate_tool_via_server(self, system_prompt: str, user_input: str,
                                   history: List[Dict] = None, session_id: str = None) -> Dict:
        """通过llama-server的chat/completions格式生成工具调用响应

        使用system+user多消息格式，优于拼接prompt方式，
        能更好地引导模型输出结构化的工具调用JSON。
        同时通过/no_think标记和低temperature抑制Qwen3的thinking模式。
        """
        import urllib.request
        import urllib.error

        model_key = 'qwen35'
        port = self.llama_server_ports.get(model_key, 8394)

        start_time = time.time()

        try:
            url = f'http://127.0.0.1:{port}/v1/chat/completions'

            # 构建消息：system + few-shot assistant/user pairs + 当前用户输入
            # 通过few-shot assistant回答作为格式示范，引导模型输出JSON
            few_shot_messages = [
                {"role": "system", "content": system_prompt + "\n\n/no_think"},
                # Few-shot: 示范工具调用格式
                {"role": "user", "content": "广州天气怎么样"},
                {"role": "assistant", "content": '{"tool_call": true, "tool": "weather_query", "parameters": {"location": "广州"}}'},
                {"role": "user", "content": "你好"},
                {"role": "assistant", "content": '您好！我是公司的智能前台助手，有什么可以帮您的吗？'},
            ]

            # 加入对话历史（如果有）
            if history:
                for turn in history:
                    role = turn.get('role', 'user')
                    content = turn.get('content', '')
                    if role == 'user':
                        few_shot_messages.append({"role": "user", "content": content})
                    elif role == 'assistant':
                        few_shot_messages.append({"role": "assistant", "content": content})

            # 当前用户输入
            few_shot_messages.append({"role": "user", "content": user_input})

            # 使用低temperature + 添加repeat_penalty抑制发散
            data = json.dumps({
                "messages": few_shot_messages,
                "max_tokens": CHAT_CONFIG['max_tokens_tool'],
                "temperature": max(CHAT_CONFIG['temperature_tool'], 0.1),
                "repeat_penalty": 1.2
            }).encode('utf-8')

            req = urllib.request.Request(
                url,
                data=data,
                headers={'Content-Type': 'application/json'},
                method='POST'
            )

            with urllib.request.urlopen(req, timeout=120) as response:
                result_json = json.loads(response.read().decode('utf-8'))

            if result_json.get('choices') and len(result_json['choices']) > 0:
                choice = result_json['choices'][0]
                msg = choice.get('message', {})
                response_text = msg.get('content', '')
                # 如果content为空但reasoning_content有值，合并使用
                if not response_text and msg.get('reasoning_content'):
                    response_text = msg.get('reasoning_content', '')

                # 检查是否有function_call响应（OpenAI function calling格式）
                if msg.get('function_call'):
                    func_call = msg['function_call']
                    tool_name = func_call.get('name', '')
                    try:
                        tool_params = json.loads(func_call.get('arguments', '{}'))
                    except json.JSONDecodeError:
                        tool_params = {}
                    return {"success": True, "text": json.dumps({
                        "tool_call": True, "tool": tool_name, "parameters": tool_params
                    }), "model": model_key, "backend": "llama-server",
                            "latency_ms": int((time.time() - start_time) * 1000)}

                # 检查是否有tool_calls响应（新版OpenAI格式）
                if msg.get('tool_calls') and len(msg['tool_calls']) > 0:
                    tc = msg['tool_calls'][0]
                    tool_name = tc.get('function', {}).get('name', '')
                    try:
                        tool_params = json.loads(tc.get('function', {}).get('arguments', '{}'))
                    except json.JSONDecodeError:
                        tool_params = {}
                    return {"success": True, "text": json.dumps({
                        "tool_call": True, "tool": tool_name, "parameters": tool_params
                    }), "model": model_key, "backend": "llama-server",
                            "latency_ms": int((time.time() - start_time) * 1000)}

                latency = int((time.time() - start_time) * 1000)
                with self.stats_lock:
                    self.stats['total_latency_ms'] += latency
                    self.stats['tool_model_calls'] += 1
                    self.stats['tool_latency_ms'] += latency

                return {"success": True, "text": response_text, "model": model_key,
                        "backend": "llama-server", "latency_ms": latency}
            else:
                return {"success": False, "error": f"llama-server返回格式错误: {result_json}"}

        except urllib.error.URLError as e:
            # llama-server失败 → 回退到拼接prompt方式
            print(f"[ModelDaemon] llama-server工具调用失败，回退: {e}", file=sys.stderr, flush=True)
            if history:
                full_prompt = self._build_prompt_with_history(system_prompt, user_input, history)
            else:
                full_prompt = f"{system_prompt}\n\n用户：{user_input}\n助手："
            return self.generate_text(
                full_prompt,
                model='qwen35',
                max_tokens=CHAT_CONFIG['max_tokens_tool'],
                temperature=CHAT_CONFIG['temperature_tool'],
                session_id=session_id
            )
        except Exception as e:
            print(f"[ModelDaemon] 工具调用server异常: {e}", file=sys.stderr, flush=True)
            return {"success": False, "error": f"工具调用server异常: {str(e)}"}

    def _format_tool_result_as_reply(self, tool_name: str, params: Dict, tool_result: Dict) -> str:
        """将工具执行结果格式化为自然语言回复"""
        result_text = tool_result.get('result', '')

        tool_reply_templates = {
            'weather_query': f"您好！为您查询到{params.get('location', '')}的天气信息：{result_text}",
            'time_query': f"您好！{result_text}",
            'calculator': f"您好！计算结果是：{result_text}",
            'translation': f"您好！翻译结果：{result_text}",
            'encyclopedia': f"您好！{result_text}",
        }

        template = tool_reply_templates.get(tool_name)
        if template:
            return template
        return f"您好！查询结果：{result_text}"

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
    SESSION_PIPE_READY_TIMEOUT_SEC = float(os.environ.get('SESSION_PIPE_READY_TIMEOUT_SEC', '2.5'))
    SESSION_PIPE_POLL_INTERVAL_SEC = 0.05

    def __init__(self, model_manager, max_workers=10):
        self.model_manager = model_manager
        self.sessions = {}
        self.session_histories = {}
        self.session_threads = {}
        self.lock = threading.Lock()
        # 根因修复：会话处理线程会长期阻塞在FIFO读，不能放在线程池里被上限卡死
        # 改为每个会话独立daemon线程，避免create_session成功但handler排队导致初始化超时
        self.max_workers = max_workers
        print("[SessionManager] 会话管理器初始化", file=sys.stderr, flush=True)

    def _wait_pipe_ready_for_write(self, pipe_path: str, timeout_sec: float) -> bool:
        """等待 FIFO 可写（有 reader 打开），避免阻塞死锁。"""
        deadline = time.time() + timeout_sec
        while time.time() < deadline:
            fd = None
            try:
                fd = os.open(pipe_path, os.O_WRONLY | os.O_NONBLOCK)
                return True
            except OSError as e:
                # ENXIO: 没有 reader；ENOENT: pipe 尚未就绪
                if e.errno in (errno.ENXIO, errno.ENOENT):
                    time.sleep(self.SESSION_PIPE_POLL_INTERVAL_SEC)
                    continue
                print(f"[SessionManager] 等待pipe可写异常 path={pipe_path}, errno={e.errno}, err={e}", file=sys.stderr, flush=True)
                return False
            finally:
                if fd is not None:
                    os.close(fd)
        return False

    def create_session(self, session_id):
        start_ts = time.time()
        with self.lock:
            if session_id in self.sessions:
                print(f"[SessionManager] 会话已存在，跳过创建: {session_id}", file=sys.stderr, flush=True)
                return True
            request_pipe = f"/tmp/dialogue_daemon_request_{session_id}"
            response_pipe = f"/tmp/dialogue_daemon_response_{session_id}"

            print(
                f"[SessionManager] create_session start session_id={session_id}, "
                f"request_pipe={request_pipe}, response_pipe={response_pipe}",
                file=sys.stderr,
                flush=True
            )

            try:
                for pipe in [request_pipe, response_pipe]:
                    if os.path.exists(pipe):
                        print(f"[SessionManager] 删除旧管道: {pipe}", file=sys.stderr, flush=True)
                        os.unlink(pipe)
                    os.mkfifo(pipe, 0o666)
                    print(f"[SessionManager] 创建会话管道成功: {pipe}", file=sys.stderr, flush=True)

                self.sessions[session_id] = {
                    "request_pipe": request_pipe,
                    "response_pipe": response_pipe,
                    "active": True
                }
                self.session_histories[session_id] = SessionHistory(CHAT_CONFIG['max_history_turns'])

                # 可观测点：检查客户端是否已接入 reader，但不作为失败条件（避免握手时序死锁）
                ready = self._wait_pipe_ready_for_write(
                    request_pipe,
                    self.SESSION_PIPE_READY_TIMEOUT_SEC
                )
                elapsed_ms = int((time.time() - start_ts) * 1000)
                if not ready:
                    print(
                        f"[SessionManager] create_session reader not ready yet (non-fatal): "
                        f"session_id={session_id}, timeout_sec={self.SESSION_PIPE_READY_TIMEOUT_SEC}, elapsed_ms={elapsed_ms}",
                        file=sys.stderr,
                        flush=True
                    )
                else:
                    print(
                        f"[SessionManager] create_session reader ready: session_id={session_id}, elapsed_ms={elapsed_ms}",
                        file=sys.stderr,
                        flush=True
                    )

                print(f"[SessionManager] 创建会话成功: {session_id}, elapsed_ms={elapsed_ms}", file=sys.stderr, flush=True)
                return True
            except Exception as e:
                elapsed_ms = int((time.time() - start_ts) * 1000)
                print(
                    f"[SessionManager] 创建会话失败: session_id={session_id}, elapsed_ms={elapsed_ms}, err={str(e)}",
                    file=sys.stderr,
                    flush=True
                )
                traceback.print_exc(file=sys.stderr)
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
                if session_id in self.session_threads:
                    del self.session_threads[session_id]
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
                        params = request.get('params', {}) if isinstance(request.get('params', {}), dict) else {}
                        service_type = request.get('service', '')

                        def get_param(key, default=None):
                            return request.get(key, params.get(key, default))
                        
                        if service_type == 'asr':
                            audio_path = get_param('audio_path', '')
                            result = self.model_manager.recognize_audio(audio_path)
                        
                        elif service_type == 'llm':
                            prompt = get_param('prompt', '')
                            model = get_param('model', 'qwen3')
                            max_tokens = get_param('max_tokens', 1000)
                            temperature = get_param('temperature', 0.7)
                            result = self.model_manager.generate_text(prompt, model, max_tokens, temperature, session_id)
                        
                        elif service_type == 'chat':
                            user_input = get_param('prompt', '')
                            history_list = get_param('history', history.get_history() if history else None)
                            result = self.model_manager.generate_chat_response(session_id, user_input, history_list)
                            
                            if result.get('success') and result.get('text') and not result.get('should_route'):
                                if history and user_input:
                                    history.add_turn('user', user_input)
                                    history.add_turn('assistant', result['text'])
                        
                        elif service_type == 'classify_intent':
                            text = get_param('text', '')
                            result = self.model_manager.classify_intent(text)
                            result['success'] = True
                        
                        elif service_type == 'tts':
                            text = get_param('text', '')
                            language = get_param('language', 'zh')
                            speed = get_param('speed', 1.0)
                            output_path = get_param('output_path', '')
                            result = self.model_manager.synthesize_speech(text, language, speed, output_path)
                        
                        elif service_type == 'speaker_register':
                            audio_path = get_param('audio_path', '')
                            speaker_id = get_param('speaker_id', '')
                            name = get_param('name', '')
                            profile = get_param('profile', {})
                            result = self.model_manager.register_speaker(audio_path, speaker_id, name, profile)
                        
                        elif service_type == 'speaker_verify':
                            audio_path = get_param('audio_path', '')
                            speaker_id = get_param('speaker_id')
                            threshold = get_param('threshold')
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
        with self.lock:
            existing = self.session_threads.get(session_id)
            if existing and existing.is_alive():
                print(f"[SessionManager] 会话处理线程已存在: {session_id}", file=sys.stderr, flush=True)
                return

            t = threading.Thread(
                target=self.handle_session,
                args=(session_id,),
                name=f"session-handler-{session_id[:8]}",
                daemon=True
            )
            self.session_threads[session_id] = t
            t.start()
            print(f"[SessionManager] 会话处理线程已启动: {session_id}", file=sys.stderr, flush=True)
    
    def get_session_count(self):
        with self.lock:
            return len([s for s in self.sessions.values() if s["active"]])
    
    def shutdown(self):
        with self.lock:
            session_ids = list(self.sessions.keys())
            threads = list(self.session_threads.values())

        for session_id in session_ids:
            self.destroy_session(session_id)

        # 等待会话线程自然退出（FIFO被unlink后会结束）
        for t in threads:
            try:
                t.join(timeout=1.0)
            except Exception:
                pass

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


def start_llm_http_server(model_manager, port=8392):
    """启动LLM的OpenAI兼容HTTP服务 — 供 fuck-u-code 等外部工具调用
    
    端点:
      GET  /v1/models          — 列出可用模型
      GET  /v1/health          — 健康检查
      POST /v1/chat/completions — OpenAI标准聊天补全
    """
    from http.server import HTTPServer, BaseHTTPRequestHandler
    
    class LLMOpenAIHandler(BaseHTTPRequestHandler):
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
            self.send_header('Access-Control-Allow-Methods', 'GET, POST')
            self.send_header('Access-Control-Allow-Headers', 'Content-Type,Authorization')
            self.end_headers()
        
        def do_GET(self):
            path = self.path.split('?')[0]
            
            if path in ('/v1/models', '/models'):
                models = []
                if model_manager.models_loaded.get('qwen35'):
                    models.append({
                        "id": "qwen3.5-2b",
                        "object": "model",
                        "owned_by": "local",
                        "created": int(time.time())
                    })
                if model_manager.models_loaded.get('qwen3'):
                    models.append({
                        "id": "qwen3-0.6b",
                        "object": "model",
                        "owned_by": "local",
                        "created": int(time.time())
                    })
                self.send_json_response({"object": "list", "data": models})
            
            elif path in ('/health', '/v1/health'):
                # 直接代理到llama-server健康检查
                server_ports = getattr(model_manager, 'llama_server_ports', {})
                if server_ports:
                    health_info = {
                        "status": "healthy",
                        "qwen35_loaded": model_manager.models_loaded.get('qwen35', False),
                        "qwen3_loaded": model_manager.models_loaded.get('qwen3', False),
                        "llama_servers": {}
                    }
                    import urllib.request
                    for model_key, port in server_ports.items():
                        try:
                            req = urllib.request.Request(f'http://127.0.0.1:{port}/health')
                            with urllib.request.urlopen(req, timeout=2) as resp:
                                health_info['llama_servers'][model_key] = json.loads(resp.read().decode('utf-8'))
                        except Exception:
                            health_info['llama_servers'][model_key] = {"status": "unreachable"}
                    self.send_json_response(health_info)
                else:
                    self.send_json_response({
                        "status": "healthy",
                        "qwen35_loaded": model_manager.models_loaded.get('qwen35', False),
                        "qwen3_loaded": model_manager.models_loaded.get('qwen3', False),
                        "llama_cli_available": model_manager.llama_cli_path is not None
                    })
            else:
                self.send_json_response({"error": {"message": "Not found"}}, 404)
        
        def do_POST(self):
            path = self.path.split('?')[0]
            
            if path not in ('/v1/chat/completions', '/chat/completions'):
                self.send_json_response({"error": {"message": "Not found"}}, 404)
                return
            
            try:
                content_length = int(self.headers.get('Content-Length', 0))
                body = json.loads(self.rfile.read(content_length))
            except Exception as e:
                self.send_json_response({"error": {"message": f"Invalid request body: {str(e)}"}}, 400)
                return
            
            # 直接代理到llama-server，不走generate_text()
            model = body.get('model', 'qwen3.5-2b')
            internal_model = 'qwen35'
            if '0.6' in model or model == 'qwen3-0.6b':
                internal_model = 'qwen3'
            
            server_ports = getattr(model_manager, 'llama_server_ports', {})
            if server_ports:
                port = server_ports.get(internal_model, 8393)
                try:
                    import urllib.request
                    # 直接转发请求到llama-server
                    proxy_req = urllib.request.Request(
                        f'http://127.0.0.1:{port}{path}',
                        data=json.dumps(body).encode('utf-8'),
                        headers={'Content-Type': 'application/json'},
                        method='POST'
                    )
                    with urllib.request.urlopen(proxy_req, timeout=120) as resp:
                        self.send_response(resp.status)
                        self.send_header('Content-Type', 'application/json; charset=utf-8')
                        self.send_header('Access-Control-Allow-Origin', '*')
                        self.end_headers()
                        self.wfile.write(resp.read())
                    return
                except Exception as e:
                    self.send_json_response({"error": {"message": f"llama-server代理失败: {str(e)}"}}, 502)
                    return
            
            # 回退：走generate_text()
            messages = body.get('messages', [])
            max_tokens = body.get('max_tokens', CHAT_CONFIG['max_tokens_tool'])
            temperature = body.get('temperature', CHAT_CONFIG['temperature_tool'])
            stream = body.get('stream', False)
            
            if stream:
                self.send_json_response({"error": {"message": "Streaming not supported"}}, 400)
                return
            
            prompt_parts = []
            for msg in messages:
                role = msg.get('role', '')
                content = msg.get('content', '')
                if role == 'system':
                    prompt_parts.append(f"System: {content}")
                elif role == 'user':
                    prompt_parts.append(f"User: {content}")
                elif role == 'assistant':
                    prompt_parts.append(f"Assistant: {content}")
            prompt = "\n".join(prompt_parts)
            
            result = model_manager.generate_text(
                prompt, model=internal_model,
                max_tokens=max_tokens, temperature=temperature
            )
            
            if result.get('success'):
                self.send_json_response({
                    "id": f"chatcmpl-{int(time.time())}",
                    "object": "chat.completion",
                    "model": model,
                    "choices": [{
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": result.get('text', '')
                        },
                        "finish_reason": "stop"
                    }],
                    "usage": {
                        "prompt_tokens": len(prompt),
                        "completion_tokens": max_tokens,
                        "total_tokens": len(prompt) + max_tokens
                    },
                    "backend": "model-daemon-llamacpp",
                    "latency_ms": result.get('latency_ms', 0)
                })
            else:
                error_msg = result.get('error', 'Unknown LLM error')
                self.send_json_response({
                    "error": {"message": error_msg, "type": "server_error"}
                }, 500)
    
    server = HTTPServer(('0.0.0.0', port), LLMOpenAIHandler)
    print(f"[ModelDaemon] 🤖 LLM OpenAI兼容HTTP服务启动于端口 {port}", file=sys.stderr, flush=True)
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
    
    # 启动声纹识别HTTP服务（仅本地模式）
    if not SPEAKER_USE_REMOTE:
        speaker_http_port = int(os.environ.get('SPEAKER_HTTP_PORT', '8391'))
        speaker_http_thread = threading.Thread(
            target=start_speaker_http_server,
            args=(manager, speaker_http_port),
            name="SpeakerHTTPServer",
            daemon=True
        )
        speaker_http_thread.start()
        print(f"[ModelDaemon] 🎤 声纹识别HTTP服务启动于端口 {speaker_http_port}", file=sys.stderr, flush=True)
    else:
        print(f"[ModelDaemon] 🎤 声纹识别使用远程服务: {SPEAKER_SERVICE_URL}，跳过本地HTTP服务", file=sys.stderr, flush=True)
    
    # 启动LLM OpenAI兼容HTTP服务（供 fuck-u-code 等外部工具调用）
    llm_http_port = int(os.environ.get('LLM_HTTP_PORT', '8392'))
    llm_http_thread = threading.Thread(
        target=start_llm_http_server,
        args=(manager, llm_http_port),
        name="LLMOpenAIHttpServer",
        daemon=True
    )
    llm_http_thread.start()
    print(f"[ModelDaemon] 🤖 LLM OpenAI兼容HTTP服务启动于端口 {llm_http_port}", file=sys.stderr, flush=True)
    
    print("[ModelDaemon] 🚀 守护进程就绪，等待请求...", file=sys.stderr, flush=True)
    
    control_request_pipe = "/tmp/dialogue_daemon_control_request"
    control_response_pipe = "/tmp/dialogue_daemon_control_response"
    
    for pipe in [control_request_pipe, control_response_pipe]:
        if os.path.exists(pipe):
            os.unlink(pipe)
        os.mkfifo(pipe, 0o666)
    
    print(f"[ModelDaemon] 创建控制管道: {control_request_pipe}", file=sys.stderr, flush=True)
    print(f"[ModelDaemon] 创建控制管道: {control_response_pipe}", file=sys.stderr, flush=True)

    control_resp_timeout_sec = float(os.environ.get('CONTROL_RESPONSE_WRITE_TIMEOUT_SEC', '2.5'))

    def write_control_response(result: Dict[str, Any], response_pipe: Optional[str] = None) -> bool:
        payload = json.dumps(result, ensure_ascii=False) + '\n'
        deadline = time.time() + control_resp_timeout_sec
        target_pipe = response_pipe or control_response_pipe
        while time.time() < deadline:
            fd = None
            try:
                fd = os.open(target_pipe, os.O_WRONLY | os.O_NONBLOCK)
                os.write(fd, payload.encode('utf-8'))
                return True
            except OSError as e:
                if e.errno in (errno.ENXIO, errno.ENOENT):
                    time.sleep(0.03)
                    continue
                print(f"[ModelDaemon] 写控制响应失败 pipe={target_pipe}, errno={e.errno}, err={e}", file=sys.stderr, flush=True)
                return False
            finally:
                if fd is not None:
                    os.close(fd)

        print(
            f"[ModelDaemon] 写控制响应超时 pipe={target_pipe}, timeout_sec={control_resp_timeout_sec}, result_keys={list(result.keys())}",
            file=sys.stderr,
            flush=True
        )
        return False

    try:
        # 根因修复：控制FIFO使用RDWR持有，避免读端重开窗口和EOF导致后续请求丢失/阻塞
        control_req_fd = os.open(control_request_pipe, os.O_RDWR)
        with os.fdopen(control_req_fd, 'r', buffering=1) as req_pipe:
            while True:
                try:
                    line = req_pipe.readline()
                    if line is None:
                        continue
                    line = line.strip()
                    if not line:
                        time.sleep(0.01)
                        continue

                    req_start = time.time()
                    request = json.loads(line)
                    action = request.get('action', '')
                    service = request.get('service', '')
                    request_id = request.get('requestId')

                    command = action or service
                    if command == 'status':
                        command = 'get_status'

                    control_response_pipe_override = None
                    params = request.get('params', {}) if isinstance(request.get('params', {}), dict) else {}
                    if isinstance(params.get('control_response_pipe'), str) and params.get('control_response_pipe').strip():
                        control_response_pipe_override = params.get('control_response_pipe').strip()

                    print(
                        f"[ModelDaemon] 控制请求: command={command}, requestId={request_id}, request={request}",
                        file=sys.stderr,
                        flush=True
                    )

                    if command == 'create_session':
                        session_id = request.get('session_id') or params.get('session_id') or ''
                        if session_id:
                            success = session_manager.create_session(session_id)
                            if success:
                                session_manager.start_session_handler(session_id)
                                print(f"[ModelDaemon] create_session handler started: {session_id}", file=sys.stderr, flush=True)
                            result = {"success": success, "session_id": session_id}
                        else:
                            result = {"success": False, "error": "缺少session_id"}

                    elif command == 'destroy_session':
                        session_id = request.get('session_id') or params.get('session_id') or ''
                        if session_id:
                            success = session_manager.destroy_session(session_id)
                            result = {"success": success, "session_id": session_id}
                        else:
                            result = {"success": False, "error": "缺少session_id"}

                    elif command == 'get_status':
                        result = {
                            "success": True,
                            "model_status": manager.get_status(),
                            "session_count": session_manager.get_session_count(),
                            "stats": manager.get_stats()
                        }

                    elif command == 'shutdown':
                        session_manager.shutdown()
                        result = {"success": True, "message": "守护进程已关闭"}
                        write_control_response(result)
                        break

                    elif command == 'llm_chat':
                        # 处理LLM聊天请求（使用高级方法，支持意图识别+快速响应）
                        model_id = params.get('model_id', 'qwen3')
                        prompt = params.get('prompt', '')
                        session_id = params.get('session_id') or f'public-{request_id}'
                        history = params.get('history', [])
                        
                        print(f"[ModelDaemon] llm_chat开始处理: prompt='{prompt[:50]}...', session_id={session_id}", file=sys.stderr, flush=True)
                        
                        if not prompt:
                            result = {"success": False, "error": "缺少prompt参数"}
                        else:
                            try:
                                print(f"[ModelDaemon] 调用generate_chat_response...", file=sys.stderr, flush=True)
                                # 使用generate_chat_response获取意图识别+快速响应+任务路由
                                gen_result = manager.generate_chat_response(
                                    session_id=session_id,
                                    user_input=prompt,
                                    history=history if history else None
                                )
                                print(f"[ModelDaemon] generate_chat_response返回: success={gen_result.get('success')}", file=sys.stderr, flush=True)
                                if gen_result.get('success'):
                                    result = {
                                        "success": True,
                                        # 同时返回text和response字段，兼容不同调用方
                                        "response": gen_result.get('text', ''),
                                        "text": gen_result.get('text', ''),
                                        "model": gen_result.get('model', model_id),
                                        "intent": gen_result.get('intent', 'casual_chat'),
                                        "confidence": gen_result.get('confidence', 0.5),
                                        "latency_ms": gen_result.get('latency_ms', 0),
                                        # 工具调用字段（公共工具独立，与公司内部工具不混合）
                                        "tool_call": gen_result.get('tool_call', False),
                                        "tool": gen_result.get('tool', ''),
                                        "parameters": gen_result.get('parameters', {}),
                                        "tool_result": gen_result.get('tool_result', ''),
                                    }
                                    # 如果是工具调用成功，日志记录
                                    if gen_result.get('tool_call'):
                                        print(f"[ModelDaemon] 公共工具调用完成: tool={gen_result.get('tool')}, result={gen_result.get('tool_result', '')[:80]}",
                                              file=sys.stderr, flush=True)
                                else:
                                    result = {
                                        "success": False,
                                        "error": gen_result.get('error', 'LLM生成失败')
                                    }
                            except Exception as e:
                                result = {"success": False, "error": f"LLM聊天异常: {str(e)}"}

                    else:
                        result = {
                            "success": False,
                            "error": f"未知操作: action={action}, service={service}",
                            "request": request
                        }

                    if request_id:
                        result["requestId"] = request_id
                    wrote = write_control_response(result, control_response_pipe_override)
                    elapsed_ms = int((time.time() - req_start) * 1000)
                    print(
                        f"[ModelDaemon] 控制响应: command={command}, requestId={request_id}, pipe={control_response_pipe_override or control_response_pipe}, wrote={wrote}, elapsed_ms={elapsed_ms}, result={result}",
                        file=sys.stderr,
                        flush=True
                    )

                except json.JSONDecodeError as e:
                    result = {"success": False, "error": f"JSON解析失败: {str(e)}"}
                    wrote = write_control_response(result)
                    print(f"[ModelDaemon] 控制请求JSON解析失败 wrote={wrote}, err={e}", file=sys.stderr, flush=True)
                except Exception as e:
                    result = {"success": False, "error": f"处理失败: {str(e)}"}
                    wrote = write_control_response(result)
                    print(f"[ModelDaemon] 控制请求处理异常 wrote={wrote}, err={e}", file=sys.stderr, flush=True)
                    traceback.print_exc(file=sys.stderr)
                
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
