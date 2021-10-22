define( [ "editor/validation-handler", "editor/json-forms" ], function ( validationHandler, jsonForms ) {

    console.log( "Service Edit Module loaded" ) ;

    var _defaultService = {
        "server": "docker"
    } ;

    var helpValues = {
        "readinessProbe": {
            "about-1": "note Only http-path is required. port defaults to 1st container port, other defaults are as shown",
            "about-2": "https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-probes/#configure-probes",
            "http-path": "/$context/devOps/health",
            "http-port": "/$context/devOps/health",
            "initialDelaySeconds": "10",
            "periodSeconds": "30",
            "timeoutSeconds": "1",
            "successThreshold": "1",
            "failureThreshold": "5"
        },
        "nodeSelector": {
            "kubernetes.io/hostname": "YOURHOST.***REMOVED***"
        },

        "scheduledJobs": {
            "scripts": [
                {
                    "description": "Remove unused images and containers (warning: kubernetes initd containers will restart)",
                    "frequency": "daily",
                    "about-frequency1": "daily, hourly, on-demand",
                    "about-frequency2": "event-pre-start, event-post-start, event-pre-deploy, event-post-deploy, event-pre-stop, event-post-stop",
                    "background": false,
                    "environment-filters": [
                        "dev.*"
                    ],
                    "hour": "03",
                    "script": "$$service-working/scripts/cleanUp.sh",
					"about-parameters": "optional: array of environment variable name, place holder value(will not be inserted), and description",
					"parameters": [
						"showUpdatesOnly,true,set to false to update the os",
						"hostsToApply,selected-hosts-on-ui,hosts will be updated sequentially - using agent cli to query events: agent/agent-start-up/your-host-name"
					]
                },
                {
                    "description": "Remove dangling images",
                    "frequency": "daily",
                    "environment-filters": [
                        "dev.*"
                    ],
                    "host-filters": [
                        ".*dev.*"
                    ],
                    "hour": "03",
                    "script": "docker volume rm $(docker volume ls -qf dangling=true)"
                }
            ]
        },

        "podAnnotations": {
            "about": "name value pairs added to the pod specification",
            "prometheus.io/path": "/actuator/prometheus",
            "prometheus.io/port": "$$service-primary-port",
            "prometheus.io/scrape": "true"
        },

        "ingressAnnotations": {
            "about": "name value pairs added to the ingress specification",
            "nginx.ingress.kubernetes.io/affinity": "cookie",
            "nginx.ingress.kubernetes.io/session-cookie-name": "k8_route",
            "nginx.ingress.kubernetes.io/session-cookie-hash": "sha1"
        },
        "kubernetesLabels": {
            "about": "note - label key and value follow dns naming conventions",
            "deployment": {
                "hi": "there"
            },
            "pod": {
                "my-pod": "this-is-value"
            },
            "service": {
                "a": "b"
            },
            "ingress": {
                "a": "b"
            }
        },
        "kubernetesResources": {
            "about": "note 1m cpu = 1/1000, ref https://kubernetes.io/docs/concepts/configuration/manage-compute-resources-container/",
            "limits": {
                "memory": "1G",
                "cpu": 1
            },
            "requests": {
                "memory": "500M",
                "about-cpu": "note 1m cpu = 1/1000",
                "cpu": "500m"
            }
        },
        "volumes": [ {
                "about": "Sample: kubernetes host volume",
                "name": "junit-demo-host-path",
                "mountPath": "/mnt/host-path",
                "hostPath": {
                    "type": "DirectoryOrCreate",
                    "path": "/opt/csap/demo-k8s"
                }
            }, {
                "about": "Sample: kubernetes empty volume",
                "name": "$$service-name-empty-dir",
                "mountPath": "/mnt/empty-dir",
                "emptyDir": {
                    "sizeLimit": "1Gi"
                }
            }, {
                "about": "Sample: kubernetes persistent volume",
                "name": "$$service-name-volume",
                "createIfNotPresent": true,
                "mountPath": "/mnt/nfs-volume",
                "persistentVolumeClaim": {
                    "claimName": "$$service-name-claim",
                    "storageClass": "csap-nfs-storage-1",
                    "accessModes": [
                        "ReadWriteOnce"
                    ],
                    "storage": "1Gi",
                    "na-selectorMatchLabels": {
                        "disk": "$$service-name-disk"
                    }
                }
            }, {
                "about": "Sample: docker temporary volume",
                "containerMount": "/my-demo-local-mount",
                "readOnly": false,
                "sharedUser": false,
                "hostPath": "$$service-name-volume",
                "createPersistent": {
                    "enabled": true,
                    "driver": "local"
                }
            }, {
                "about": "Sample: docker host volume",
                "containerMount": "/my-demo-host-mount",
                "readOnly": true,
                "sharedUser": true,
                "hostPath": "/opt/csap/my-demo-host-fs",
                "createPersistent": {
                    "enabled": true,
                    "driver": "host"
                }
            } ],
        "portMappings": [
            {
                "about": "Sample: kubernetes",
                "containerPort": "$$service-primary-port"
            },
            {
                "about": "Sample: kubernetes with a service port and name",
                "containerPort": "$$service-primary-port",
                "servicePort": "$$service-primary-port"
            },

            {
                "about": "Sample: kubernetes optional params",
                "containerPort": "$$service-primary-port",
                "hostPort": "9091",
                "protocol": "TCP|UDP",
                "servicePort": "$$service-primary-port",
                "name-note": "name must be 14 or fewer characters, and needed when multiple ports are exported",
                "name": "http-$$service-primary-port"
            },
            {
                "about": "Sample: docker container(Private) and host(Public) port ",
                "PrivatePort": "$$service-primary-port",
                "PublicPort": "$$service-primary-port"
            } ],
        "scripts": [
            {
                "description": "run script on demand",
                "frequency": "onDemand",
                "script": "$staging/bin/checkLimits.sh"
            },
            {
                "description": "run script after deploy",
                "frequency": "event-post-deploy",
                "script": "$resources/common/csap-events.sh"
            },
            {
                "description": "spec: wait for pod startup",
                "frequency": "event-post-deploy",
                "script": "wait_for_pod_log my-pod-pattern my-log-pattern my-pod-count"
            },
            {
                "description": "docker: wait for pod startup",
                "frequency": "event-post-start",
                "script": "wait_for_pod_log my-pod-pattern my-log-pattern my-pod-count"
            },
            {
                "description": "wait for pod shutdown",
                "frequency": "event-post-stop",
                "script": "wait_for_pod_removed my-pod-pattern"
            }, {
                "description": "run script after stopping",
                "frequency": "event-post-stop",
                "script": "$resources/common/csap-events.sh"
            } ]
    }

    var defaultValues = {

        "parameters": "-Xms128M -Xmx128M",

        "remoteCollections": [ {
                "host": "csap-dev01",
                "port": "8996"
            }, {
                "host": "csap-dev02",
                "port": "8996"
            } ],
        "environmentVariables": {
            "configuration-maps": [ "map-for-testing" ],
            "variable_1": "value_1",
            "variable_2": "value_2"
        },

        "environment-overload": {
            "dev": { },
            "test": { },
            "performace": { },
            "production": { },
            "demoEnv": {
                "parameters": "-Xms2056M -Xmx2056M",
                "maven": {
                    "dependency": "org.sample:my-service:2.0.0:jar"
                }
            }
        },

        "nodeSelector": {
            "kubernetes.io/hostname": "YOURHOST.***REMOVED***"
        },

        "scheduledJobs": {
            "scripts": [
                {
                    "description": "run script on demand",
                    "frequency": "onDemand",
                    "script": "$staging/bin/checkLimits.sh"
                },
                {
                    "description": "run script after deploy",
                    "frequency": "event-post-deploy",
                    "script": "$resources/common/csap-events.sh"
                },
                {
                    "description": "spec: wait for pod startup",
                    "frequency": "event-post-deploy",
                    "script": "wait_for_pod_log my-pod-pattern my-log-pattern my-pod-count"
                },
                {
                    "description": "docker: wait for pod startup",
                    "frequency": "event-post-start",
                    "script": "wait_for_pod_log my-pod-pattern my-log-pattern my-pod-count"
                },
                {
                    "description": "wait for pod shutdown",
                    "frequency": "event-post-stop",
                    "script": "wait_for_pod_removed my-pod-pattern"
                }, {
                    "description": "run script after stopping",
                    "frequency": "event-post-stop",
                    "script": "$resources/common/csap-events.sh"
                } ],
            "diskCleanUp": [ {
                    "path": "$workingFolder/logs/serviceJobs/*.log",
                    "olderThenDays": 7,
                    "maxDepth": 1
                }, {
                    "path": "$processing/*.logs",
                    "olderThenDays": 30,
                    "maxDepth": 1
                }, {
                    "path": "$staging/mavenRepo",
                    "olderThenDays": 60
                }, {
                    "path": "$workingFolder/temp",
                    "olderThenDays": 60
                } ],
            "logRotation": [
                {
                    "path": "$$service-logs/consoleLogs.txt",
                    "settings": "copytruncate,weekly,rotate 3,compress,missingok,size 10M"
                },
                {
                    "path": "$$service-logs/$$service-name.logs",
                    "settings": "copytruncate,weekly,rotate 3,compress,missingok,size 10M"
                },
                {
                    "path": "$logFolder/warnings.log",
                    "lifecycles": "dev,stage,lt",
                    "settings": "copytruncate,weekly,rotate 3,compress,missingok,size 10M"
                },
                {
                    "path": "$logFolder/warnings.log",
                    "lifecycles": "prod",
                    "settings": "copytruncate,weekly,rotate 3,compress,missingok,size 10M"
                } ]
        },
        "apacheModJk": {
            "comments": "It is strongly recommended to set timeout to 30000 (30 seconds)",
            "loadBalance": [ "method=Request", "sticky_session=1" ],
            "connection": [ "reply_timeout=15" ]
        },
        "apacheModRewrite": [
            "RewriteRule ^/test1/(.*)$  /ServletSample/$1 [PT]",
            "RewriteRule ^/test2/(.*)$  /ServletSample/$1 [PT]" ],

        "performance": {
            "systemCpu": {
                "title": "Sample 1: Host Cpu",
                "mbean": "java.lang:type=OperatingSystem",
                "attribute": "SystemCpuLoad"
            },
            "processCpu": {
                "title": "Sample 2: JVM Cpu",
                "mbean": "java.lang:type=OperatingSystem",
                "attribute": "ProcessCpuLoad",
                "max": 10
            },
            "requests": {
                "title": "Sample 3: MBean With Delta Collector",
                "mbean": "Catalina:j2eeType=Servlet,WebModule=__CONTEXT__,name=dispatcher,J2EEApplication=none,J2EEServer=none",
                "attribute": "requestCount",
                "delta": "delta"
            }
        },
        "docker": {
            "entryPoint": 'default from image - specified as an array: [  "docker-entrypoint.sh" ] ',
            "command": 'default from image - specified as an array: [   "nginx",   "-g",   "daemon off;"  ]',
            "run": '-d --name $$service-name --privileged -v $HOME/docker-nfs-export:/nfsshare -e SHARED_DIRECTORY=/nfsshare itsthenetwork/nfs-server-alpine:latest',

            "deployment-file-names": [ "file-1.yaml", "file-2.yaml" ],

            "kubernetes-settings": {
                "node-selectors": {
                    "kubernetes.io/hostname": "YOURHOST.***REMOVED***"
                },
                "pod-annotations": {
                    "sample-annotation-1": "sample-value-1"
                },
                "ingress-annotations": {
                    "nginx.ingress.kubernetes.io/affinity": "cookie",
                    "nginx.ingress.kubernetes.io/session-cookie-name": "k8_route",
                    "nginx.ingress.kubernetes.io/session-cookie-hash": "sha1"
                },
                "readinessProbe": {
                    "http-path": "/actuator/health"
                },
                "livenessProbe": {
                    "http-path": "/actuator/health"
                },
                "labelsByType": {
                    "deployment": {
                        "hi": "there"
                    },
                    "pod": {
                        "my-pod": "this-is-value"
                    }
                },
                "resources": {
                    "about": "note 1m cpu = 1/1000",
                    "limits": {
                        "memory": "1G",
                        "cpu": 1
                    },
                    "requests": {
                        "memory": "500M",
                        "cpu": "500m"
                    }
                }
            },

            "network": {
                "name": "myNetwork",
                "note": "name can be bridge, host, or custom network name",
                "createPersistent": {
                    "enabled": true,
                    "driver": "bridge"
                }
            },
            "environmentVariables": [ "MY_TEST=some value",
                "anotherTest=anotherValue" ],
            "volumes": [ {
                    "about": "Sample: kubernetes persistent volume",
                    "name": "$$service-name-volume",
                    "mountPath": "/mnt/nfs-volume",
                    "persistentVolumeClaim": {
                        "claimName": "$$service-name-claim",
                        "storageClass": "csap-nfs-storage-1",
                        "createIfNotPresent": true
                    }
                }, {
                    "about": "Sample: docker local volume",
                    "containerMount": "/my-demo-local-mount",
                    "readOnly": false,
                    "sharedUser": false,
                    "hostPath": "$$service-name-volume",
                    "createPersistent": {
                        "enabled": true,
                        "driver": "local"
                    }
                } ],
            "portMappings": [
                {
                    "about": "Sample: kubernetes",
                    "containerPort": "$$service-primary-port",
                    "servicePort": "$$service-primary-port"
                },
                {
                    "about": "Sample: docker",
                    "PrivatePort": "$$service-primary-port",
                    "PublicPort": "$$service-primary-port"
                } ],
            "limits": {
                "ulimits": [ {
                        "name": "nofile",
                        "soft": 500,
                        "hard": 500
                    }, {
                        "name": "nproc",
                        "soft": 200,
                        "hard": 200
                    } ],
                "logs": {
                    "type": "json-file",
                    "max-size": "10m",
                    "max-file": "2"
                }
            }
        }

    }
    return {

        defaultFields: function () {
            return defaultValues ;
        },

        help: function () {
            return helpValues ;
        },
        defaultService: function () {
            // clone the objecy so subsequent adds do not get the same
            return JSON.parse(JSON.stringify(_defaultService));  ;
        },

    }
} ) ;
