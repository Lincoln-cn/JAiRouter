// JAiRouter 文档站点谷歌广告集成

(function() {
    'use strict';
    
    // 广告配置
    const AD_CONFIG = {
        client: 'ca-pub-8229708772289943',
        enabled: true,
        placements: {
            sidebar: {
                slot: 'auto',
                format: 'vertical',
                responsive: true
            }
        }
    };
    
    // 检查广告拦截器
    function isAdBlockerEnabled() {
        try {
            const testAd = document.createElement('div');
            testAd.innerHTML = '&nbsp;';
            testAd.className = 'adsbox';
            testAd.style.position = 'absolute';
            testAd.style.left = '-10000px';
            document.body.appendChild(testAd);
            
            const isBlocked = testAd.offsetHeight === 0;
            document.body.removeChild(testAd);
            return isBlocked;
        } catch (e) {
            return false;
        }
    }
    
    // 创建广告容器
    function createAdContainer(placement = 'sidebar') {
        const adContainer = document.createElement('div');
        adContainer.className = `google-ad google-ad-${placement}`;
        adContainer.setAttribute('data-placement', placement);
        
        // 创建广告单元
        const adUnit = document.createElement('ins');
        adUnit.className = 'adsbygoogle';
        adUnit.style.display = 'block';
        adUnit.setAttribute('data-ad-client', AD_CONFIG.client);
        adUnit.setAttribute('data-ad-slot', AD_CONFIG.placements[placement].slot);
        adUnit.setAttribute('data-ad-format', AD_CONFIG.placements[placement].format);
        
        if (AD_CONFIG.placements[placement].responsive) {
            adUnit.setAttribute('data-full-width-responsive', 'true');
        }
        
        adContainer.appendChild(adUnit);
        return adContainer;
    }
    
    // 在导航栏下方插入广告
    function insertSidebarAd() {
        const navigation = document.querySelector('.md-nav--primary');
        if (!navigation) {
            return false;
        }
        
        // 检查是否已经插入了广告
        if (navigation.querySelector('.google-ad-sidebar')) {
            return false;
        }
        
        const adContainer = createAdContainer('sidebar');
        
        // 添加标题
        const adTitle = document.createElement('div');
        adTitle.className = 'google-ad-title';
        adTitle.textContent = '赞助内容';
        adContainer.insertBefore(adTitle, adContainer.firstChild);
        
        // 插入到导航的末尾
        navigation.appendChild(adContainer);
        
        return true;
    }
    
    // 初始化广告
    function initializeAds() {
        if (!AD_CONFIG.enabled) {
            return;
        }
        
        // 检查广告拦截器
        if (isAdBlockerEnabled()) {
            console.log('Ad blocker detected, ads will not be displayed');
            return;
        }
        
        // 等待页面加载和导航渲染
        const initAd = () => {
            if (insertSidebarAd()) {
                // 初始化 AdSense
                try {
                    if (window.adsbygoogle) {
                        (window.adsbygoogle = window.adsbygoogle || []).push({});
                    }
                } catch (e) {
                    console.error('Error initializing Google Ads:', e);
                }
            } else {
                // 如果导航还没有渲染，等待一段时间后重试
                setTimeout(initAd, 500);
            }
        };
        
        // 页面加载完成后初始化广告
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', initAd);
        } else {
            // 等待一段时间确保导航已渲染
            setTimeout(initAd, 100);
        }
    }
    
    // 响应式广告调整
    function adjustAdsForMobile() {
        const ads = document.querySelectorAll('.google-ad');
        const isMobile = window.innerWidth <= 768;
        
        ads.forEach(ad => {
            if (isMobile) {
                ad.style.display = 'none';
            } else {
                ad.style.display = 'block';
            }
        });
    }
    
    // 监听窗口大小变化
    function setupResponsiveAds() {
        window.addEventListener('resize', function() {
            clearTimeout(this.resizeTimeout);
            this.resizeTimeout = setTimeout(adjustAdsForMobile, 250);
        });
        
        // 初始调整
        adjustAdsForMobile();
    }
    
    // 广告性能监控
    function setupAdPerformanceMonitoring() {
        // 监控广告加载时间
        const observer = new MutationObserver(function(mutations) {
            mutations.forEach(function(mutation) {
                mutation.addedNodes.forEach(function(node) {
                    if (node.nodeType === 1 && node.classList && node.classList.contains('google-ad')) {
                        const startTime = performance.now();
                        
                        // 监控广告是否成功加载
                        const checkAdLoad = setInterval(() => {
                            const adUnit = node.querySelector('.adsbygoogle');
                            if (adUnit && adUnit.getAttribute('data-adsbygoogle-status') === 'done') {
                                const loadTime = performance.now() - startTime;
                                console.log(`Ad loaded in ${loadTime.toFixed(2)}ms`);
                                clearInterval(checkAdLoad);
                            }
                        }, 100);
                        
                        // 超时处理
                        setTimeout(() => {
                            clearInterval(checkAdLoad);
                        }, 10000);
                    }
                });
            });
        });
        
        observer.observe(document.body, {
            childList: true,
            subtree: true
        });
    }
    
    // 处理页面路由变化（SPA应用）
    function handleRouteChange() {
        // 监听 pushState 和 replaceState
        const originalPushState = history.pushState;
        const originalReplaceState = history.replaceState;
        
        history.pushState = function() {
            originalPushState.apply(history, arguments);
            setTimeout(initializeAds, 100);
        };
        
        history.replaceState = function() {
            originalReplaceState.apply(history, arguments);
            setTimeout(initializeAds, 100);
        };
        
        // 监听 popstate 事件
        window.addEventListener('popstate', function() {
            setTimeout(initializeAds, 100);
        });
    }
    
    // 初始化所有功能
    function init() {
        initializeAds();
        setupResponsiveAds();
        setupAdPerformanceMonitoring();
        handleRouteChange();
    }
    
    // 启动初始化
    init();
    
    // 导出配置供外部使用
    window.GoogleAdsConfig = {
        enable: function() {
            AD_CONFIG.enabled = true;
            initializeAds();
        },
        disable: function() {
            AD_CONFIG.enabled = false;
            const ads = document.querySelectorAll('.google-ad');
            ads.forEach(ad => ad.remove());
        },
        isEnabled: function() {
            return AD_CONFIG.enabled;
        }
    };
    
})();