#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
追踪数据生成测试脚本 (Python版本)

此脚本用于生成追踪数据，测试追踪概览页面的数据显示功能

使用方法:
    python generate-tracing-data.py [base_url] [count]

参数:
    base_url: 服务器地址，默认为 http://localhost:8080
    count: 生成请求数量，默认为 50

示例:
    python generate-tracing-data.py
    python generate-tracing-data.py http://localhost:8080 100
    python generate-tracing-data.py https://your-server.com 200

功能:
    - 生成多种类型的API请求 (聊天、嵌入、模型列表等)
    - 模拟真实的用户行为模式
    - 随机生成错误请求 (约10%概率)
    - 控制并发数避免服务器过载
    - 验证生成的追踪数据

注意:
    - 确保目标服务器正在运行
    - 需要Python 3.6或更高版本
    - 需要安装 requests 库: pip install requests
"""

import sys
import json
import time
import random
import argparse
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Dict, List, Any, Optional, Tuple

try:
    import requests
    from requests.adapters import HTTPAdapter
    from urllib3.util.retry import Retry
except ImportError:
    print("错误: 需要安装 requests 库")
    print("请运行: pip install requests")
    sys.exit(1)


class Colors:
    """终端颜色常量"""
    RED = '\033[0;31m'
    GREEN = '\033[0;32m'
    YELLOW = '\033[1;33m'
    BLUE = '\033[0;34m'
    CYAN = '\033[0;36m'
    NC = '\033[0m'  # No Color


class Logger:
    """日志工具类"""
    
    @staticmethod
    def info(message: str):
        print(f"{Colors.BLUE}[INFO]{Colors.NC} {message}")
    
    @staticmethod
    def success(message: str):
        print(f"{Colors.GREEN}[SUCCESS]{Colors.NC} {message}")
    
    @staticmethod
    def warning(message: str):
        print(f"{Colors.YELLOW}[WARNING]{Colors.NC} {message}")
    
    @staticmethod
    def error(message: str):
        print(f"{Colors.RED}[ERROR]{Colors.NC} {message}")


class TracingDataGenerator:
    """追踪数据生成器"""
    
    def __init__(self, base_url: str, concurrent_requests: int = 5):
        self.base_url = base_url.rstrip('/')
        self.concurrent_requests = concurrent_requests
        self.session = self._create_session()
        
        # 模拟数据
        self.model_names = [
            "gpt-3.5-turbo", "gpt-4", "claude-3-sonnet", "gemini-pro"
        ]
        
        self.embedding_models = [
            "text-embedding-ada-002", "text-embedding-3-small", "text-embedding-3-large"
        ]
        
        self.user_messages = [
            "Hello, how are you today?",
            "What is the weather like?",
            "Can you help me with programming?",
            "Tell me a joke",
            "Explain quantum computing",
            "What is machine learning?",
            "How to cook pasta?",
            "Recommend a good book",
            "What is the capital of France?",
            "How to learn a new language?",
            "What are the benefits of exercise?",
            "How does photosynthesis work?",
            "Explain the theory of relativity",
            "What is blockchain technology?",
            "How to improve memory?"
        ]
        
        self.embedding_texts = [
            "This is a sample text for embedding",
            "Machine learning is fascinating",
            "Natural language processing",
            "Artificial intelligence applications",
            "Deep learning algorithms",
            "Computer vision techniques",
            "Reinforcement learning methods",
            "Neural network architectures",
            "Data science methodologies",
            "Statistical analysis approaches"
        ]
    
    def _create_session(self) -> requests.Session:
        """创建HTTP会话，配置重试策略"""
        session = requests.Session()
        
        # 配置重试策略
        retry_strategy = Retry(
            total=3,
            backoff_factor=1,
            status_forcelist=[429, 500, 502, 503, 504],
        )
        
        adapter = HTTPAdapter(max_retries=retry_strategy)
        session.mount("http://", adapter)
        session.mount("https://", adapter)
        
        return session
    
    def check_server_connection(self) -> bool:
        """检查服务器连接"""
        Logger.info(f"检查服务器连接: {self.base_url}")
        
        try:
            response = self.session.get(
                f"{self.base_url}/actuator/health",
                timeout=5
            )
            if response.status_code == 200:
                Logger.success("服务器连接正常")
                return True
            else:
                Logger.error(f"服务器返回状态码: {response.status_code}")
                return False
        except requests.exceptions.RequestException as e:
            Logger.error(f"无法连接到服务器: {self.base_url}")
            Logger.error(f"错误详情: {str(e)}")
            return False
    
    def generate_chat_request(self) -> Dict[str, Any]:
        """生成随机聊天请求"""
        return {
            "model": random.choice(self.model_names),
            "messages": [
                {
                    "role": "user",
                    "content": random.choice(self.user_messages)
                }
            ],
            "max_tokens": random.randint(50, 200),
            "temperature": round(random.uniform(0.1, 1.0), 1)
        }
    
    def generate_embedding_request(self) -> Dict[str, Any]:
        """生成随机嵌入请求"""
        return {
            "model": random.choice(self.embedding_models),
            "input": random.choice(self.embedding_texts)
        }
    
    def send_request(self, endpoint: str, data: Dict[str, Any], request_id: int) -> Dict[str, Any]:
        """发送单个请求"""
        start_time = time.time()
        
        # 随机决定是否模拟错误（10%概率）
        simulate_error = random.random() < 0.1
        
        headers = {
            "Content-Type": "application/json",
            "X-API-Key": "dev-admin-12345-abcde-67890-fghij",
            "X-Request-ID": f"req-{request_id}-{int(time.time())}"
        }
        
        if simulate_error:
            headers["X-Simulate-Error"] = "true"
        
        try:
            url = f"{self.base_url}{endpoint}"
            
            if endpoint in ["/v1/models", "/actuator/health"]:
                response = self.session.get(url, headers=headers, timeout=30)
            else:
                response = self.session.post(
                    url, 
                    json=data, 
                    headers=headers, 
                    timeout=30
                )
            
            end_time = time.time()
            duration = int((end_time - start_time) * 1000)  # 转换为毫秒
            
            status_code = response.status_code
            
            # 输出结果
            if 200 <= status_code < 300:
                print(f"✓ Request {request_id}: {endpoint} - {status_code} ({duration}ms)")
                success = True
            elif 400 <= status_code < 500:
                print(f"⚠ Request {request_id}: {endpoint} - {status_code} ({duration}ms) [Client Error]")
                success = False
            else:
                print(f"✗ Request {request_id}: {endpoint} - {status_code} ({duration}ms) [Server Error]")
                success = False
            
            return {
                "request_id": request_id,
                "endpoint": endpoint,
                "success": success,
                "status_code": status_code,
                "duration": duration,
                "error": None
            }
            
        except requests.exceptions.RequestException as e:
            end_time = time.time()
            duration = int((end_time - start_time) * 1000)
            
            print(f"? Request {request_id}: {endpoint} - Connection failed ({duration}ms)")
            print(f"  Error: {str(e)}")
            
            return {
                "request_id": request_id,
                "endpoint": endpoint,
                "success": False,
                "status_code": 0,
                "duration": duration,
                "error": str(e)
            }
    
    def generate_tracing_data(self, request_count: int) -> List[Dict[str, Any]]:
        """生成追踪数据"""
        Logger.info("开始生成追踪数据...")
        Logger.info(f"目标服务器: {self.base_url}")
        Logger.info(f"请求数量: {request_count}")
        Logger.info(f"并发数: {self.concurrent_requests}")
        
        results = []
        
        # 准备请求任务
        tasks = []
        for i in range(1, request_count + 1):
            # 随机选择API端点
            endpoint_type = random.randint(0, 3)
            
            if endpoint_type == 0:
                endpoint = "/v1/chat/completions"
                data = self.generate_chat_request()
            elif endpoint_type == 1:
                endpoint = "/v1/embeddings"
                data = self.generate_embedding_request()
            elif endpoint_type == 2:
                endpoint = "/v1/models"
                data = {}
            else:
                endpoint = "/actuator/health"
                data = {}
            
            tasks.append((endpoint, data, i))
        
        # 使用线程池执行请求
        with ThreadPoolExecutor(max_workers=self.concurrent_requests) as executor:
            # 提交所有任务
            future_to_task = {
                executor.submit(self.send_request, endpoint, data, request_id): (endpoint, request_id)
                for endpoint, data, request_id in tasks
            }
            
            # 收集结果
            completed = 0
            for future in as_completed(future_to_task):
                try:
                    result = future.result()
                    results.append(result)
                    completed += 1
                    
                    # 显示进度
                    if completed % 10 == 0 or completed == request_count:
                        Logger.info(f"已完成 {completed}/{request_count} 个请求...")
                    
                except Exception as e:
                    endpoint, request_id = future_to_task[future]
                    Logger.error(f"请求 {request_id} 执行异常: {str(e)}")
            
            # 随机延迟，模拟真实流量
            time.sleep(random.uniform(0.1, 1.0))
        
        Logger.success("追踪数据生成完成！")
        return results
    
    def verify_tracing_data(self) -> None:
        """验证追踪数据"""
        Logger.info("验证追踪数据...")
        
        # 等待数据处理
        time.sleep(2)
        
        try:
            # 检查追踪统计
            response = self.session.get(f"{self.base_url}/api/tracing/query/statistics", timeout=10)
            if response.status_code == 200:
                stats_data = response.json()
                
                Logger.info("追踪统计结果:")
                Logger.info(f"  总追踪数: {stats_data.get('totalTraces', 0)}")
                Logger.info(f"  错误追踪数: {stats_data.get('errorTraces', 0)}")
                Logger.info(f"  平均耗时: {stats_data.get('avgDuration', 0)}ms")
                
                if stats_data.get('totalTraces', 0) > 0:
                    Logger.success("追踪数据验证成功！")
                else:
                    Logger.warning("未检测到追踪数据，可能需要等待数据处理完成")
            else:
                Logger.warning(f"无法获取追踪统计数据，状态码: {response.status_code}")
                
        except requests.exceptions.RequestException as e:
            Logger.warning(f"无法获取追踪统计数据: {str(e)}")
        
        try:
            # 检查服务统计
            response = self.session.get(f"{self.base_url}/api/tracing/query/services", timeout=10)
            if response.status_code == 200:
                services_data = response.json()
                
                Logger.info(f"服务统计: 发现 {len(services_data)} 个服务")
                
                if services_data:
                    Logger.info("服务列表:")
                    for service in services_data:
                        name = service.get('name', 'unknown')
                        traces = service.get('traces', 0)
                        errors = service.get('errors', 0)
                        avg_duration = service.get('avgDuration', 0)
                        Logger.info(f"  - {name}: {traces} traces, {errors} errors, {avg_duration}ms avg")
            else:
                Logger.warning(f"无法获取服务统计数据，状态码: {response.status_code}")
                
        except requests.exceptions.RequestException as e:
            Logger.warning(f"无法获取服务统计数据: {str(e)}")


def show_usage():
    """显示使用说明"""
    print("""
追踪数据生成测试脚本 (Python版本)

使用方法:
  python generate-tracing-data.py [base_url] [count]

参数:
  base_url    服务器地址 (默认: http://localhost:8080)
  count       生成请求数量 (默认: 50)

示例:
  python generate-tracing-data.py                                    # 使用默认参数
  python generate-tracing-data.py http://localhost:8080 100         # 生成100个请求
  python generate-tracing-data.py https://your-server.com 200       # 向远程服务器发送200个请求

功能:
  - 生成多种类型的API请求 (聊天、嵌入、模型列表等)
  - 模拟真实的用户行为模式
  - 随机生成错误请求 (约10%概率)
  - 控制并发数避免服务器过载
  - 验证生成的追踪数据

注意:
  - 确保目标服务器正在运行
  - 需要Python 3.6或更高版本
  - 需要安装 requests 库: pip install requests
""")


def main():
    """主函数"""
    # 显示标题
    print("=" * 40)
    print(f"{Colors.CYAN}        追踪数据生成测试脚本{Colors.NC}")
    print(f"{Colors.CYAN}        (Python版本){Colors.NC}")
    print("=" * 40)
    print()
    
    # 解析命令行参数
    parser = argparse.ArgumentParser(
        description="生成追踪数据测试脚本",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  python generate-tracing-data.py
  python generate-tracing-data.py http://localhost:8080 100
  python generate-tracing-data.py https://your-server.com 200
        """
    )
    
    parser.add_argument(
        'base_url',
        nargs='?',
        default='http://localhost:8080',
        help='服务器地址 (默认: http://localhost:8080)'
    )
    
    parser.add_argument(
        'count',
        nargs='?',
        type=int,
        default=50,
        help='生成请求数量 (默认: 50)'
    )
    
    parser.add_argument(
        '--concurrent',
        type=int,
        default=5,
        help='并发请求数 (默认: 5)'
    )
    
    parser.add_argument(
        '--help-usage',
        action='store_true',
        help='显示详细使用说明'
    )
    
    args = parser.parse_args()
    
    if args.help_usage:
        show_usage()
        return
    
    # 验证参数
    if args.count <= 0:
        Logger.error("请求数量必须大于0")
        return
    
    if args.concurrent <= 0:
        Logger.error("并发数必须大于0")
        return
    
    # 创建生成器
    generator = TracingDataGenerator(args.base_url, args.concurrent)
    
    # 检查服务器连接
    if not generator.check_server_connection():
        return
    
    # 生成追踪数据
    results = generator.generate_tracing_data(args.count)
    
    # 验证数据
    generator.verify_tracing_data()
    
    # 统计结果
    success_count = sum(1 for r in results if r['success'] and 200 <= r['status_code'] < 300)
    error_count = len(results) - success_count
    
    print()
    Logger.success("测试完成！")
    Logger.info(f"请求统计: 成功 {success_count}, 失败 {error_count}")
    Logger.info("现在可以访问追踪概览页面查看生成的数据:")
    Logger.info(f"  {args.base_url} (前端页面)")
    Logger.info(f"  {args.base_url}/api/tracing/query/statistics (统计API)")
    Logger.info(f"  {args.base_url}/api/tracing/query/services (服务API)")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print(f"\n{Colors.YELLOW}[INFO]{Colors.NC} 用户中断执行")
        sys.exit(0)
    except Exception as e:
        Logger.error(f"执行过程中发生异常: {str(e)}")
        sys.exit(1)