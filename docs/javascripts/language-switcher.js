// JAiRouter 文档站点语言切换功能

(function() {
    'use strict';
    
    // 语言配置
    const LANGUAGES = {
        'zh': {
            name: '中文',
            code: 'zh',
            path: '/'
        },
        'en': {
            name: 'English',
            code: 'en',
            path: '/en/'
        }
    };
    
    // 获取当前语言
    function getCurrentLanguage() {
        const path = window.location.pathname;
        if (path.startsWith('/en/')) {
            return 'en';
        }
        return 'zh';
    }
    
    function getLanguagePath(targetLang, currentPath) {
        const currentLang = getCurrentLanguage();
    
        // 移除语言前缀，保留原始路径结构
        let cleanPath = currentPath;
    
        if (currentLang === 'en') {
            cleanPath = cleanPath.replace(/^\/en(\/|$)/, '/');
        } else {
            cleanPath = cleanPath.replace(/^\/zh(\/|$)/, '/');
        }
    
        // 确保路径以 / 开头
        if (!cleanPath.startsWith('/')) {
            cleanPath = '/' + cleanPath;
        }
    
        // 添加目标语言前缀
        if (targetLang === 'en') {
            return '/en' + (cleanPath === '/' ? '' : cleanPath);
        } else {
            return cleanPath === '/en' ? '/' : cleanPath.replace(/^\/en/, '');
        }
    }
    
    // 创建语言切换器
    function createLanguageSwitcher() {
        const switcher = document.createElement('div');
        switcher.className = 'language-switcher';
        switcher.setAttribute('aria-label', 'Language Switcher');
        
        const currentLang = getCurrentLanguage();
        const currentPath = window.location.pathname;
        
        Object.keys(LANGUAGES).forEach(langCode => {
            const lang = LANGUAGES[langCode];
            const link = document.createElement('a');
            link.href = getLanguagePath(langCode, currentPath);
            link.textContent = lang.name;
            link.setAttribute('hreflang', lang.code);
            link.setAttribute('title', `Switch to ${lang.name}`);
            
            if (langCode === currentLang) {
                link.className = 'active';
                link.setAttribute('aria-current', 'page');
            }
            
            // 添加点击事件
            link.addEventListener('click', function(e) {
                // 检查目标页面是否存在
                const targetPath = this.href;
                
                // 添加加载指示器
                link.style.opacity = '0.6';
                link.style.pointerEvents = 'none';
                
                // 恢复状态（防止页面不存在时卡住）
                setTimeout(() => {
                    link.style.opacity = '';
                    link.style.pointerEvents = '';
                }, 3000);
            });
            
            switcher.appendChild(link);
        });
        
        return switcher;
    }
    
    // 初始化语言切换器
    function initLanguageSwitcher() {
        // 等待页面加载完成
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', initLanguageSwitcher);
            return;
        }
        
        // 检查是否已存在语言切换器
        if (document.querySelector('.language-switcher')) {
            return;
        }
        
        // 创建并添加语言切换器
        const switcher = createLanguageSwitcher();
        document.body.appendChild(switcher);
        
        // 添加键盘快捷键支持
        document.addEventListener('keydown', function(e) {
            // Alt + L 切换语言
            if (e.altKey && e.key === 'l') {
                e.preventDefault();
                const currentLang = getCurrentLanguage();
                const targetLang = currentLang === 'zh' ? 'en' : 'zh';
                const targetPath = getLanguagePath(targetLang, window.location.pathname);
                window.location.href = targetPath;
            }
        });
    }
    
    // 添加语言检测和自动跳转功能
    function detectAndRedirectLanguage() {
        // 只在首页进行语言检测
        if (window.location.pathname !== '/' && !window.location.pathname.startsWith('/en/')) {
            return;
        }
        
        // 检查是否有语言偏好设置
        const savedLang = localStorage.getItem('preferred-language');
        if (savedLang && LANGUAGES[savedLang]) {
            const currentLang = getCurrentLanguage();
            if (savedLang !== currentLang) {
                const targetPath = getLanguagePath(savedLang, window.location.pathname);
                window.location.href = targetPath;
                return;
            }
        }
        
        // 检测浏览器语言
        const browserLang = navigator.language || navigator.userLanguage;
        const langCode = browserLang.toLowerCase().startsWith('zh') ? 'zh' : 'en';
        
        // 保存语言偏好
        localStorage.setItem('preferred-language', langCode);
        
        // 如果当前语言与检测到的语言不同，进行跳转
        const currentLang = getCurrentLanguage();
        if (langCode !== currentLang && window.location.pathname === '/') {
            const targetPath = getLanguagePath(langCode, window.location.pathname);
            window.location.href = targetPath;
        }
    }
    
    // 添加搜索增强功能
    function enhanceSearch() {
        // 等待搜索框加载
        const searchInput = document.querySelector('.md-search__input');
        if (!searchInput) {
            setTimeout(enhanceSearch, 100);
            return;
        }
        
        // 添加搜索快捷键提示
        searchInput.setAttribute('placeholder', 
            getCurrentLanguage() === 'zh' ? '搜索文档... (Ctrl+K)' : 'Search docs... (Ctrl+K)'
        );
        
        // 添加 Ctrl+K 快捷键
        document.addEventListener('keydown', function(e) {
            if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
                e.preventDefault();
                searchInput.focus();
            }
        });
    }
    
    // 添加页面性能监控
    function addPerformanceMonitoring() {
        if ('performance' in window) {
            window.addEventListener('load', function() {
                setTimeout(function() {
                    const perfData = performance.getEntriesByType('navigation')[0];
                    if (perfData) {
                        const loadTime = perfData.loadEventEnd - perfData.loadEventStart;
                        console.log(`Page load time: ${loadTime}ms`);
                        
                        // 如果加载时间过长，显示提示
                        if (loadTime > 3000) {
                            console.warn('Page load time is slow. Consider optimizing resources.');
                        }
                    }
                }, 0);
            });
        }
    }
    
    // 添加离线支持检测
    function addOfflineSupport() {
        if ('serviceWorker' in navigator) {
            // 这里可以注册 Service Worker 来支持离线访问
            console.log('Service Worker support detected');
        }
        
        // 监听网络状态变化
        window.addEventListener('online', function() {
            console.log('Network connection restored');
        });
        
        window.addEventListener('offline', function() {
            console.log('Network connection lost');
        });
    }
    
    // 初始化所有功能
    function init() {
        detectAndRedirectLanguage();
        initLanguageSwitcher();
        enhanceSearch();
        addPerformanceMonitoring();
        addOfflineSupport();
    }
    
    // 启动初始化
    init();
    
    // 导出全局函数供其他脚本使用
    window.JAiRouterDocs = {
        getCurrentLanguage: getCurrentLanguage,
        switchLanguage: function(langCode) {
            if (LANGUAGES[langCode]) {
                const targetPath = getLanguagePath(langCode, window.location.pathname);
                window.location.href = targetPath;
            }
        }
    };
    
})();
