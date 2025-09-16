#!/bin/bash

# JWT账户管理接口修复验证脚本

echo "开始验证JWT账户管理接口修复..."

# 1. 获取初始账户列表
echo "1. 获取初始账户列表:"
curl -s -X GET http://localhost:8080/api/security/jwt/accounts \
  -H "Jairouter_Token: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGVzIjpbIkFETUlOIiwiVVNFUiJdLCJpc3MiOiJqYWlyb3V0ZXItZGV2IiwiaWF0IjoxNzU3OTM2NTMzLCJleHAiOjE3NTc5MzgzMzN9.djQ5xfg1CMC9B7MsVcsVpHfHpiOSPBU_uKLjj4BPGb4" | jq '.'

# 2. 禁用一个账户
echo -e "\n2. 禁用admin账户:"
curl -s -X PATCH "http://localhost:8080/api/security/jwt/accounts/admin/status?enabled=false" \
  -H "Jairouter_Token: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGVzIjpbIkFETUlOIiwiVVNFUiJdLCJpc3MiOiJqYWlyb3V0ZXItZGV2IiwiaWF0IjoxNzU3OTM2NTMzLCJleHAiOjE3NTc5MzgzMzN9.djQ5xfg1CMC9B7MsVcsVpHfHpiOSPBU_uKLjj4BPGb4" \
  -H "Content-Type: application/json" | jq '.'

# 3. 再次获取账户列表
echo -e "\n3. 再次获取账户列表:"
curl -s -X GET http://localhost:8080/api/security/jwt/accounts \
  -H "Jairouter_Token: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGVzIjpbIkFETUlOIiwiVVNFUiJdLCJpc3MiOiJqYWlyb3V0ZXItZGV2IiwiaWF0IjoxNzU3OTM2NTMzLCJleHAiOjE3NTc5MzgzMzN9.djQ5xfg1CMC9B7MsVcsVpHfHpiOSPBU_uKLjj4BPGb4" | jq '.'

# 4. 启用账户
echo -e "\n4. 启用admin账户:"
curl -s -X PATCH "http://localhost:8080/api/security/jwt/accounts/admin/status?enabled=true" \
  -H "Jairouter_Token: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGVzIjpbIkFETUlOIiwiVVNFUiJdLCJpc3MiOiJqYWlyb3V0ZXItZGV2IiwiaWF0IjoxNzU3OTM2NTMzLCJleHAiOjE3NTc5MzgzMzN9.djQ5xfg1CMC9B7MsVcsVpHfHpiOSPBU_uKLjj4BPGb4" \
  -H "Content-Type: application/json" | jq '.'

# 5. 最后获取账户列表
echo -e "\n5. 最后获取账户列表:"
curl -s -X GET http://localhost:8080/api/security/jwt/accounts \
  -H "Jairouter_Token: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGVzIjpbIkFETUlOIiwiVVNFUiJdLCJpc3MiOiJqYWlyb3V0ZXItZGV2IiwiaWF0IjoxNzU3OTM2NTMzLCJleHAiOjE3NTc5MzgzMzN9.djQ5xfg1CMC9B7MsVcsVpHfHpiOSPBU_uKLjj4BPGb4" | jq '.'

echo -e "\n验证完成。请检查是否还有UnsupportedOperationException错误。"