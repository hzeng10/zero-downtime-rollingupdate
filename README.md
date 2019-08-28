# A demo service to show how to implement the zero downtime rolling update in Kubernetes

In Kubernetes environment, it provides rolling update function which can help to implement the zero downtime deployment.
However, when redeploy the same project in Istio enabled environment, there are some HTTP requests with 503 Service Unavailable status code.

The Istio cluster-internal networking mode looks a bit differently compared to plain Kubernetes. Envoy proxy also performs active health checking of microservice pods, it will detect outliers and ultimately prevent connections to them. HTTP-based readiness probes that are defined for pods will also be included and executed by the Envoy proxies. In other words, the proxy containers won’t connect to pods whose readiness probes fail, even if the pods would still accept requests. Retry configuration which can be added to the sidecar proxies through configuration only mitigates but doesn’t resolve this issue.

There is a reasonable solution is to use Istio subsets as version designators and to re-route the service traffic independent of Kubernetes' rolling update mechanism. It uses a service subset(please refer k8s\destination-rule-v1-v2.yaml) to identify the application’s version, such as v1 or v2, and configure the virtual service to route to one specific version, please refer k8s\istio-virtual-service-v1-v2.yaml for detail. The Istio proxies routes which are configured by the virtual service resources can be re-routed to different subset versions with real zero-downtime.

In order to use that approach, it requires to create separate Kubernetes deployments, one for each individual version of our application, and perform the actual switch via Istio.

This project is created by using Spring boot, and it exposes a REST API controller. See the apis detailed below. It uses openjdk:8-jdk-alpine as base docker image.

This project requires Java 8, Maven, MiniKube, Istio and Fortio. This project uses Fortio as a loading test tool to generate HTTP traffics.

### Build Container

Building the container is a multi-step process. 

**Note**: The Dockerfile will attempt to use the artifact `target/zero-downtime-rollingupdate-*.jar`. If your target jar is named differently or if this regex may be ambigious, please make the appropriate changes in your Dockerfile.

##### Mac users

```
make build
```

##### Windows users

```
docker build -t zero-downtime-rollingupdate-builder -f Dockerfile.build .
docker run -v m2:/root/.m2 -v <absolute_path_to_source_code>:/build zero-downtime-rollingupdate-builder
docker build -t zero-downtime-rollingupdate-img .
```

### Run Container

##### Using docker compose

Docker compose is a convenient way of running docker containers. For launching the application using docker-compose, ensure the builds steps are already executed.

```
docker-compose up --build

Remote debugging using docker compose is enabled. 
```

##### Using docker command

```
docker run -it -p 8080:8080 -p 8082:8082 zero-downtime-rollingupdate-img

```

To run container locally use:

```
docker run -it -p 8080:8080 -p 8082:8082 zero-downtime-rollingupdate-img
```

To debug container using remote debugging.

```
make debug
```

To view container logs, run following command:

```
docker logs <container id>
```

### List of available APIs

API | Description
--- | ---
`GET /ping` | Returns string 'pong'. Used for basic healthcheck.
`GET /k8s/demo` | A dummy request for demo purpose when deploy it into Kubernetes environment.
`GET /k8s/shutdown` | Simulate "SIGTERM" to monitor shutdown behaviour in Kubernetes environment.
`GET /k8s/memoryleak` | Simulate JVM OutOfMemory Error to monitor shutdown behaviour in Kubernetes environment.
`GET /k8s/longTask` | Simulate long running task to monitor shutdown behaviour in Kubernetes environment.
`GET /k8s/fullgc` | Simulate JVM GC to monitor shutdown behaviour in Kubernetes environment.



APIs can be accessed via curl command: `curl http://localhost:8080/<API>`

### Setup MiniKube
#### Installation
Follow below installation links to install Minikube on Linux. 
https://kubernetes.io/docs/tasks/tools/install-minikube/
In above installation steps, the Minikube also needs virtualization support, choose Virtual Box as the VM for Minikube cluster. 
##### Minikube useful commands
Start Minikube, and create a cluster
```
minikube start
```
Access Kubernetes dashboard in a browser
```
minikube dashboard
```
Access Kubernetes service
```
minikube service <service name>
```

Stop Minikube
```
minikube stop
```
Delete local Minikube cluster
```
minikube delete
```
### Setup Istio
Istio is installed in its own istio-system namespace and can manage services from all other namespaces.

From a macOS or Linux system, run the following command to download the latest release. 
```
curl -L https://git.io/getLatestIstio | ISTIO_VERSION=1.2.4 sh -
```
Move to Istio package directory, and add istioctl client to PATH environment variable.
```
export PATH=$PWD/bin:$PATH
```
Apply the Istio Custom Resource Definitions(CRDs) 
```
for i in install/kubernetes/helm/istio-init/files/crd*yaml; do kubectl apply -f $i; done
```
Edit the demo yaml file to update "ClusterIP" as "NodePort" via any text editor, and apply the demo profile.
```
kubectl apply -f install/kubernetes/istio-demo.yaml
```
Verify the installation to make sure the Istio related services are running. The Istio ingress-gateway port can be found from below screenshot. The port 80:31380/TCP is NodePort. 
```
kubectl get service -n istio-system
```
### Setup Fortio
Fortio is a loading test tool which can be used to test REST API of micro service, Istio integration etc. It's a fast, small (3Mb docker image, minimal dependencies), reusable, embeddable go library as well as a command line tool and server process, the server includes a simple web UI and graphical representation of the results (both a single latency graph and a multiple results comparative min, max, avg, qps and percentiles graphs). For more detail, please visit this link https://github.com/fortio/fortio
#### Installation
Mac
```
brew install fortio
```
Linux
```
curl -L https://github.com/fortio/fortio/releases/download/v1.3.1/fortio-linux_x64-1.3.1.tgz \
| sudo tar -C / -xvzpf -
```
##### Usage
Start Fortio server
Once the server is started, it can be accessed via http://localhost:8080/fortio/, change the setting like QPS, test duration, target URL etc, click "Start" button to launch the loading test.
```
fortio server
```
Start Fortio server with different port
```
fortio server -http-port 9090
fortio server -http-port 192.168.230.217:8090
```
### Deploy zero-downtime-rollingupdate Spring Boot application
Enable Istio injection for default name space. 
```
kubectl label namespace default istio-injection=enabled
```
Create ConfigMap object into Kubernetes
```
kubectl apply -f ~/zero-downtime-rollingupdate/k8s/configmap.yaml
```
Create Gateway object into Kubernetes
```
kubectl apply -f ~/zero-downtime-rollingupdate/k8s/gateway.yaml
```
Create VirtualService object into Kubernetes.
```
kubectl apply -f ~/zero-downtime-rollingupdate/k8s/virtualservice.yaml
```
Apply the  deployment yaml file
```
kubectl apply -f ~/zero-downtime-rollingupdate/k8s/deployment.yaml
```
Verify the application can be accessed via Istio gateway(Assume the IP is:192.168.99.101). The Istio ingress gateway port can be found via kubectl get services -n istio-system
```
curl -v http://192.168.99.101:31380/k8s/demo
```