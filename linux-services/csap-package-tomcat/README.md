
# csap-package-tomcat

## Provides

- support for tomcat 7.x 8.x 8.5.x 9.x

- integrated into csap-core-service:
	- specified per service: same host can be running 7.x, and 9.x
	- ability to select tomcat version in both admin ui and application definition
	- supports rolling upgrades: old versions remain in place

References: http://tomcat.apache.org/

## Configuration

- by default, tomcat package is located using service name **tomcat**
	- if desired, package can be named to anything, corresponding references in linux package will need to be updated
- tomcat releases are extracted to CSAP Runtime/appsTomcat


```
"environmentVariables": {

}
```

Note: upload tomcat releases to your [ tools server](https://github.com/csap-platform/csap-core/tree/master/csap-core-install)