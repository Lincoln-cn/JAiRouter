#!/bin/bash

# 检查JWT账号数据的脚本

echo "=== 检查JWT账号配置 ==="
echo ""

# 1. 检查配置文件
echo "1. 检查配置文件中的JWT账号配置版本："
if [ -f "config/jwt-accounts-config@1.json" ]; then
    echo "找到配置文件: config/jwt-accounts-config@1.json"
    cat config/jwt-accounts-config@1.json | jq '.'
else
    echo "未找到配置文件"
fi

echo ""
echo "2. 检查所有JWT账号配置版本文件："
ls -la config/jwt-accounts-config@*.json 2>/dev/null || echo "没有找到任何版本文件"

echo ""
echo "3. 检查H2数据库文件："
ls -la data/config.mv.db 2>/dev/null || echo "H2数据库文件不存在"

echo ""
echo "4. 检查应用日志（最后50行）："
if [ -f "logs/application.log" ]; then
    tail -50 logs/application.log | grep -i "jwt.*account\|创建.*账户"
else
    echo "日志文件不存在"
fi

echo ""
echo "=== 检查完成 ==="
