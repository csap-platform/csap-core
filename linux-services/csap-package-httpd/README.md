
# csap-package-httpd

## Provides

builds apache web server build from source, uploads binary as part of package to repository
- includes modjk integraton to enable dynamically add/update/delete of service endpoints
- integration with csap-core to provide dynamic loading of endpoints, monitoring, etc.

References: [httpd](https://httpd.apache.org/docs/) ,  [modjk](https://tomcat.apache.org/connectors-doc/reference/apache.html)

## Configuration

For performance analysis - add the following to the Service Definition
```
{
	"config": {
		"httpCollectionUrl": "http://localhost.cisco.com:8080/server-status?auto",
		"patternMatch": ": ([^\n]*)",
		"title": "CSAP Collection Url"
	},
	"BusyWorkers": {
		"attribute": "BusyWorkers",
		"title": "Workers Busy"
	},
	"IdleWorkers": {
		"attribute": "IdleWorkers",
		"title": "Workers Idle"
	},
	"KBytesPerSecond": {
		"attribute": "BytesPerSec",
		"decimals": "1",
		"divideBy": 1024,
		"title": "Requests: KB per second"
	},
	"KBytesPerRequest": {
		"attribute": "BytesPerReq",
		"decimals": "1",
		"divideBy": 1024,
		"title": "Requests: KB per Request"
	},
	"UrlsProcessed": {
		"attribute": "Total Accesses",
		"delta": true,
		"title": "Requests: Between collections"
	},
	"RequestsPerSecond": {
		"attribute": "ReqPerSec",
		"decimals": "2",
		"title": "Requests: Number per Second"
	}
}
```

Note: upload httpd source to your [ tools server](https://github.com/csap-platform/csap-core/tree/master/csap-core-install)