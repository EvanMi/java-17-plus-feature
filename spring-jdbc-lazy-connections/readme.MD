curl -s 'localhost:8080/actuator/metrics/hikaricp.connections.usage' | jq '.measurements'
curl http://127.0.0.1:8080/register/3