
# csap-package-docker

## Provides

- multiple docker package support
	- docker-ce: latest community edition
	- docker-latest: latest available in RH distributions
	- docker: older version (not recommended) 
	
- supported OS centos and other redhat based distributions. Other OSs can be added on request

- integrated into csap-core-service:
	- csap file explorer: support for browsing docker container file systems
	- csap application editor: support for specifying image to pull and container configuration and deployment
	- csap host dashboard: adhoc pulls of images, container, creates, ...

References: https://docs.docker.com/

## Configuration

- by default, the docker package in the yum repository will be installed (currently 1.12)
	- if the $csapVersion string contains "LATEST" docker-latest package will be installed (currently 1.13)

- csap-core-service defaults to using "docker" as the service locator. when creating - use docker as the service name

- creates docker storage using specified env variable.
	- docker storage can grow over time in development environments. 
	docker package includes a cleanup script to prune unused storage volumes, images, etc 


```
	"environmentVariables": {
		"dockerStorage": "/opt/csap/dockerStorage",
		"allowRemote": "true",
		"dockerPackage": "docker-latest"
	},
	
	
	"scheduledJobs": {
		"scripts": [
			{
				"description": "docker system prune",
				"frequency": "hourly",
				"hour": "03",
				"script": "$workingFolder/scripts/cleanUp.sh"
			}
		]
	}
	
```