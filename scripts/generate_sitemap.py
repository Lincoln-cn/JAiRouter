#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Sitemap Generator for JAiRouter Documentation
Automatically generates sitemap.xml based on the documentation structure
"""

import os
import datetime
from pathlib import Path

# Configuration
BASE_URL = "https://jairouter.com"
DOCS_DIR = "docs"
SITEMAP_FILE = os.path.join(DOCS_DIR, "sitemap.xml")

# Priority mapping for different types of pages
PRIORITY_MAP = {
    'index.md': 1.0,
    'getting-started': 0.9,
    'configuration': 0.8,
    'api-reference': 0.8,
    'deployment': 0.8,
    'monitoring': 0.7,
    'security': 0.7,
    'tracing': 0.7,
    'troubleshooting': 0.6,
    'development': 0.6,
    'reference': 0.5,
    'default': 0.5
}

# Change frequency mapping
CHANGE_FREQ_MAP = {
    'index.md': 'weekly',
    'getting-started': 'monthly',
    'configuration': 'monthly',
    'api-reference': 'monthly',
    'deployment': 'monthly',
    'monitoring': 'weekly',
    'security': 'weekly',
    'tracing': 'weekly',
    'troubleshooting': 'monthly',
    'development': 'weekly',
    'reference': 'monthly',
    'default': 'monthly'
}

def get_priority_and_changefreq(file_path, dir_name):
    """Determine priority and change frequency based on file path and directory"""
    file_name = os.path.basename(file_path)
    
    # Check for exact file matches first
    if file_name in PRIORITY_MAP:
        priority = PRIORITY_MAP[file_name]
        changefreq = CHANGE_FREQ_MAP[file_name]
        return priority, changefreq
    
    # Check for directory matches
    for key in PRIORITY_MAP:
        if key in dir_name:
            priority = PRIORITY_MAP[key]
            changefreq = CHANGE_FREQ_MAP[key]
            return priority, changefreq
    
    # Default values
    return PRIORITY_MAP['default'], CHANGE_FREQ_MAP['default']

def get_last_modified_time(file_path):
    """Get the last modified time of a file"""
    try:
        mtime = os.path.getmtime(file_path)
        return datetime.datetime.fromtimestamp(mtime).strftime('%Y-%m-%d')
    except:
        # Return current date if unable to get file time
        return datetime.datetime.now().strftime('%Y-%m-%d')

def scan_markdown_files(docs_dir):
    """Scan for all markdown files in the documentation directory"""
    md_files = []
    
    # Walk through the docs directory
    for root, dirs, files in os.walk(docs_dir):
        for file in files:
            if file.endswith('.md'):
                full_path = os.path.join(root, file)
                relative_path = os.path.relpath(full_path, docs_dir)
                md_files.append((full_path, relative_path))
    
    return md_files

def generate_url_entry(relative_path, lastmod, priority, changefreq):
    """Generate a single URL entry for the sitemap"""
    # Convert file path to URL path
    url_path = relative_path.replace('\\', '/').replace('.md', '/')
    
    # Handle index files
    if url_path.endswith('index/'):
        url_path = url_path[:-6]  # Remove 'index/'
    
    # Generate alternate links for multilingual support
    alternate_links = ""
    if url_path == '' or url_path == '/':  # Root index
        loc = BASE_URL + '/'
        alternate_links = f'''
    <xhtml:link rel="alternate" hreflang="zh" href="{loc}"/>
    <xhtml:link rel="alternate" hreflang="en" href="{BASE_URL}/en/"/>'''
    elif url_path.startswith('en/'):
        # English pages
        loc = BASE_URL + '/' + url_path
        zh_path = url_path[3:]  # Remove 'en/' prefix
        if zh_path == '' or zh_path == '/':
            zh_url = BASE_URL + '/'
        else:
            zh_url = BASE_URL + '/' + zh_path
        alternate_links = f'''
    <xhtml:link rel="alternate" hreflang="en" href="{loc}"/>
    <xhtml:link rel="alternate" hreflang="zh" href="{zh_url}"/>'''
    else:
        # Chinese pages (default language)
        if url_path == '' or url_path == '/':
            loc = BASE_URL + '/'
            en_url = BASE_URL + '/en/'
        else:
            loc = BASE_URL + '/' + url_path
            en_url = BASE_URL + '/en/' + url_path
        
        alternate_links = f'''
    <xhtml:link rel="alternate" hreflang="zh" href="{loc}"/>
    <xhtml:link rel="alternate" hreflang="en" href="{en_url}"/>'''
    
    # Generate the URL entry
    url_entry = f'''  <url>
    <loc>{loc}</loc>
    <lastmod>{lastmod}</lastmod>
    <changefreq>{changefreq}</changefreq>
    <priority>{priority:.1f}</priority>{alternate_links}
  </url>'''
    
    return url_entry

def generate_sitemap():
    """Generate the complete sitemap.xml file"""
    # Scan for markdown files
    md_files = scan_markdown_files(DOCS_DIR)
    
    # Start building the sitemap content
    sitemap_content = '''<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"
        xmlns:xhtml="http://www.w3.org/1999/xhtml">
'''
    
    # Process each markdown file
    url_entries = []
    processed_paths = set()  # Keep track of processed paths to avoid duplicates
    
    for full_path, relative_path in md_files:
        # Get directory name for priority determination
        dir_name = os.path.dirname(relative_path)
        
        # Get priority and change frequency
        priority, changefreq = get_priority_and_changefreq(full_path, dir_name)
        
        # Get last modified time
        lastmod = get_last_modified_time(full_path)
        
        # Convert file path to URL path for duplicate checking
        url_path = relative_path.replace('\\', '/').replace('.md', '/')
        if url_path.endswith('index/'):
            url_path = url_path[:-6]
            
        # Avoid duplicate entries
        if url_path in processed_paths:
            continue
        processed_paths.add(url_path)
        
        # Generate URL entry
        url_entry = generate_url_entry(relative_path, lastmod, priority, changefreq)
        url_entries.append((priority, url_entry))
    
    # Sort entries by priority (highest first)
    url_entries.sort(key=lambda x: x[0], reverse=True)
    
    # Add sorted entries to sitemap content
    for _, url_entry in url_entries:
        sitemap_content += url_entry + '\n'
    
    # Close the sitemap
    sitemap_content += '</urlset>'
    
    # Write the sitemap to file
    with open(SITEMAP_FILE, 'w', encoding='utf-8') as f:
        f.write(sitemap_content)
    
    print(f"Sitemap generated successfully: {SITEMAP_FILE}")
    print(f"Total URLs included: {len(url_entries)}")

if __name__ == "__main__":
    generate_sitemap()