kubectl config set-context docker-desktop
kubectl apply -f kubernetes/namespace.json
kubectl config set-context --current --namespace=wsdm-akka-1
kubectl apply -f kubernetes/akka-cluster.yml
kubectl apply -f kubernetes/mongodb-deploy.yaml
