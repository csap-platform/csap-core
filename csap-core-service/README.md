
# csap-core-service

## Provides
csap-core-service enables service management, host management, data collection, and analytics portal.

References: [Reference Guide](https://github.com/csap-platform/csap-core/wiki)
 -

### Configuration

Command Line parameters: 
- uses spring profiles to configure whether running in agent or admin/analytics modes
```
-Dspring.profiles.active=CSAP_LIFE,agent,limits
```
 

Environment Variables:
```
{
	"hostUrlPattern": "http://CSAP_HOST.yourcompany.com:8011/CsAgent",
	"mailServer": "outbound.yourcompany.com",
	"csapDockerRepository": "containers.yourcompany.com/pnightin"
}
```



### Desktop development:
- Some tests require provisioned systems, such as LDAP, git, etc.
	- **application-company.yml**  is a small subset useful for quickly getting started
- refer to application.yml and application-company.yml for complete set of variables

- dependencies defined using maven, so any IDE works  
- create csap folder in your home directory, copy and modify
	- csapSecurity.properties
	- application-company.yml
- add the following parameter to your IDE start command 
	- ```--spring.config.location=file:c:/Users/yourHomeDir/csap/```
- add the following parameter to your JVM properties
	- ```-DcsapTest=/Users/yourHomeDir/csap/```

### Unit tests
- add the following to your env: ```-DcsapTest="/Users/yourHomeDir/csap/"```


References: [Spring Boot Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html)


### agent bootstrap

1. 5s: JVM Load, spring early init
1. 1s: http client pools
1. 1s: http listener sockets
1. 2s: load application definition(s) from *.json
1. <1s: Event publisher  init, Agent Connection Pool init
1. <1s: start resource collectors
1. Application Init 1 completed
	- note that NO kubernetes namespace mapping is available at this point. namespaces csap services
	 will be dynamically added after initial ps scans determine which namespace pods are on the specific host
1. Service Init loading
	- 3s: getProcessStatus 1
	- checkAndWaitFor initialscans
	- 16s getProcessStatus 2
	- namespace detection - application definition reload with dynamic services (namespace) added

	