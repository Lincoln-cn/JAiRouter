// Language switcher functionality for JAiRouter documentation
(function() {
    'use strict';

    // Language configuration
    const LANGUAGES = {
        'zh': {
            name: '中文',
            flag: '🇨🇳',
            path: '/zh/'
        },
        'en': {
            name: 'English',
            flag: '🇺🇸',
            path: '/en/'
        }
    };

    // Get current language from URL
    function getCurrentLanguage() {
        const path = window.location.pathname;
        if (path.startsWith('/zh/')) return 'zh';
        if (path.startsWith('/en/')) return 'en';
        return 'zh'; // Default to Chinese
    }

    // Get corresponding page path in target language
    function getCorrespondingPath(targetLang) {
        const currentPath = window.location.pathname;
        const currentLang = getCurrentLanguage();
        
        // Remove current language prefix
        let pagePath = currentPath;
        if (currentLang === 'zh' && pagePath.startsWith('/zh/')) {
            pagePath = pagePath.substring(3);
        } else if (currentLang === 'en' && pagePath.startsWith('/en/')) {
            pagePath = pagePath.substring(3);
        }
        
        // Add target language prefix
        if (targetLang === 'zh') {
            return '/zh' + (pagePath.startsWith('/') ? pagePath : '/' + pagePath);
        } else if (targetLang === 'en') {
            return '/en' + (pagePath.startsWith('/') ? pagePath : '/' + pagePath);
        }
        
        return pagePath;
    }

    // Create language switcher element
    function createLanguageSwitcher() {
        const currentLang = getCurrentLanguage();
        const currentLangConfig = LANGUAGES[currentLang];
        
        if (!currentLangConfig) return null;

        const switcher = document.createElement('div');
        switcher.className = 'md-header__option language-switcher';
        
        const select = document.createElement('div');
        select.className = 'md-select';
        
        const inner = document.createElement('div');
        inner.className = 'md-select__inner';
        inner.innerHTML = `
            <span class="language-${currentLang}">${currentLangConfig.flag} ${currentLangConfig.name}</span>
            <svg class="md-icon" viewBox="0 0 24 24" width="16" height="16">
                <path d="M7 10l5 5 5-5z"/>
            </svg>
        `;
        
        const list = document.createElement('div');
        list.className = 'md-select__list';
        list.style.display = 'none';
        
        // Add language options
        Object.keys(LANGUAGES).forEach(langCode => {
            if (langCode !== currentLang) {
                const langConfig = LANGUAGES[langCode];
                const item = document.createElement('a');
                item.className = `md-select__item language-${langCode}`;
                item.href = getCorrespondingPath(langCode);
                item.innerHTML = `${langConfig.flag} ${langConfig.name}`;
                list.appendChild(item);
            }
        });
        
        // Toggle dropdown on click
        inner.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            const isVisible = list.style.display !== 'none';
            list.style.display = isVisible ? 'none' : 'block';
        });
        
        // Close dropdown when clicking outside
        document.addEventListener('click', function() {
            list.style.display = 'none';
        });
        
        select.appendChild(inner);
        select.appendChild(list);
        switcher.appendChild(select);
        
        return switcher;
    }

    // Insert language switcher into header
    function insertLanguageSwitcher() {
        const header = document.querySelector('.md-header__inner');
        if (!header) return;
        
        // Remove existing language switcher
        const existing = header.querySelector('.language-switcher');
        if (existing) {
            existing.remove();
        }
        
        const switcher = createLanguageSwitcher();
        if (switcher) {
            // Insert before the search button or at the end
            const searchButton = header.querySelector('.md-header__button[for="__search"]');
            if (searchButton) {
                header.insertBefore(switcher, searchButton);
            } else {
                header.appendChild(switcher);
            }
        }
    }

    // Update page title based on language
    function updatePageTitle() {
        const currentLang = getCurrentLanguage();
        const titleElement = document.querySelector('title');
        
        if (titleElement && currentLang === 'en') {
            // Update title for English pages
            const title = titleElement.textContent;
            if (title.includes('JAiRouter 文档')) {
                titleElement.textContent = title.replace('JAiRouter 文档', 'JAiRouter Documentation');
            }
        }
    }

    // Update navigation labels for current language
    function updateNavigationLabels() {
        const currentLang = getCurrentLanguage();
        
        if (currentLang === 'en') {
            // Update common navigation labels for English
            const navItems = document.querySelectorAll('.md-nav__link');
            navItems.forEach(item => {
                const text = item.textContent.trim();
                // Add translations as needed
                switch (text) {
                    case '首页':
                        item.textContent = 'Home';
                        break;
                    case '快速开始':
                        item.textContent = 'Getting Started';
                        break;
                    case '配置指南':
                        item.textContent = 'Configuration';
                        break;
                    case 'API参考':
                        item.textContent = 'API Reference';
                        break;
                    case '部署指南':
                        item.textContent = 'Deployment';
                        break;
                    case '监控指南':
                        item.textContent = 'Monitoring';
                        break;
                    case '开发指南':
                        item.textContent = 'Development';
                        break;
                    case '故障排查':
                        item.textContent = 'Troubleshooting';
                        break;
                    case '参考资料':
                        item.textContent = 'Reference';
                        break;
                }
            });
        }
    }

    // Initialize language switcher when DOM is ready
    function init() {
        insertLanguageSwitcher();
        updatePageTitle();
        updateNavigationLabels();
        
        // Re-initialize on navigation changes (for SPA-like behavior)
        const observer = new MutationObserver(function(mutations) {
            mutations.forEach(function(mutation) {
                if (mutation.type === 'childList') {
                    // Check if header was modified
                    const headerModified = Array.from(mutation.addedNodes).some(node => 
                        node.nodeType === Node.ELEMENT_NODE && 
                        (node.classList.contains('md-header') || node.querySelector('.md-header'))
                    );
                    
                    if (headerModified) {
                        setTimeout(insertLanguageSwitcher, 100);
                    }
                }
            });
        });
        
        observer.observe(document.body, {
            childList: true,
            subtree: true
        });
    }

    // Initialize when DOM is loaded
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    // Handle browser back/forward navigation
    window.addEventListener('popstate', function() {
        setTimeout(function() {
            insertLanguageSwitcher();
            updatePageTitle();
            updateNavigationLabels();
        }, 100);
    });

})();