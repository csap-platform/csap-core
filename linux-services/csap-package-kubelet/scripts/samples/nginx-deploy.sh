


kubectl apply -f nginx.yaml

kubectl describe deployment yourcompany-nginx-deployment

kubectl expose deployment yourcompany-nginx-deployment --port=80 --type=LoadBalancer

# undo the above