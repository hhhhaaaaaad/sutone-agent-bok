curl https://apis.itedus.cn/v1/chat/completions -H "Content-Type: application/json" -H "Authorization: Bearer sk-****b9eB22aA69747F9Be0d626030B4Bf64" -d '{
  "model": "gpt-5.1",
  "messages": [
    {
      "role": "user",
      "content": "1+1"
    }
  ]
}'