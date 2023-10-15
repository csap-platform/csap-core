//
//  csap monitoring definition is compiled using jsonet to generate kubernetes manifests (*.yaml)
//		- jsonnet language: https://jsonnet.org/
//		- source templates: https://github.com/prometheus-operator/kube-prometheus
//


local kp =

  (import 'kube-prometheus/main.libsonnet') +
  (import 'kube-prometheus/addons/all-namespaces.libsonnet') +
  (import 'kube-prometheus/addons/node-ports.libsonnet') +
  // (import 'kube-prometheus/platforms/kubeadm.libsonnet') +
  
  {
	  values+:: {
	  
	      common+: {
	        namespace: 'csap-monitoring',
	        platform: 'kubeadm',
	      },
	      
	      prometheus+: {
	        namespaces: [],
	      },
	      
	      // kubePrometheus+: {
	      //   platform: 'kubeadm',
	      // },
	    
//	      grafana+:: {
//	        config: {  // http://docs.grafana.org/installation/configuration/
//	          sections: {
//	            // Do not require grafana users to login/authenticate
//	            'auth.anonymous': { enabled: true },
//	          },
//	        },
//	      }, // grafana
	      
	    }, // values
	
	    prometheus+:: {
	      
	      prometheus+: {
	      
	      
	        // https://github.com/coreos/prometheus-operator/blob/master/Documentation/api.md#prometheusspec
	        spec+: { 
	        
	          replicas: 1,
	        
	          // "^([0-9]+)(y|w|d|h|m|s|ms)$" (years weeks days hours minutes seconds milliseconds)
	          // scrapeInterval: '1m', // ref: https://prometheus.io/docs/prometheus/latest/configuration/configuration/
	          // scrapeTimeout: '10s',

	          retention: '__data_retention__',
	          
	
	          // Ref:: https://github.com/coreos/prometheus-operator/blob/master/Documentation/user-guides/storage.md
	          // https://github.com/coreos/prometheus-operator/blob/master/Documentation/api.md#storagespec
	          storage: {
	          
	          	// https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.11/#persistentvolumeclaim-v1-core 
	          	//   defines variable named 'spec' of type 'PersistentVolumeClaimSpec'
	            volumeClaimTemplate: {  
	              apiVersion: 'v1',
	              kind: 'PersistentVolumeClaim',
	              spec: {
	                accessModes: ['ReadWriteOnce'],
	                
	                
	                // requests: https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.11/#resourcerequirements-v1-core
	                // storage: https://kubernetes.io/docs/concepts/policy/resource-quotas/#storage-resource-quota
	                resources: { requests: { storage: '__data_volume_size__' } },
	                
	                // must exist prior to kube-prometheus being deployed.
	                storageClassName: '__data_storage_class__',
	                
	                // The following 'selector' is only needed if you're using manual storage provisioning 
	                // selector: { matchLabels: {} },
	              },
	            },
	          },  // storage
	        },  // spec
	      },  // prometheus
	    },  // prometheus

  }; 

  //
  //  
  //
  
{ '00namespace-namespace': kp.kubePrometheus.namespace } +
{
  ['0prometheus-operator-' + name]: kp.prometheusOperator[name]
  for name in std.filter((function(name) name != 'serviceMonitor' && name != 'prometheusRule'), std.objectFields(kp.prometheusOperator))
} +
// serviceMonitor and prometheusRule are separated so that they can be created after the CRDs are ready
{ 'prometheus-operator-serviceMonitor': kp.prometheusOperator.serviceMonitor } +
{ 'prometheus-operator-prometheusRule': kp.prometheusOperator.prometheusRule } +
{ 'kube-prometheus-prometheusRule': kp.kubePrometheus.prometheusRule } +
{ ['alertmanager-' + name]: kp.alertmanager[name] for name in std.objectFields(kp.alertmanager) } +
//{ ['blackbox-exporter-' + name]: kp.blackboxExporter[name] for name in std.objectFields(kp.blackboxExporter) } +
{ ['grafana-' + name]: kp.grafana[name] for name in std.objectFields(kp.grafana) } +
{ ['kube-state-metrics-' + name]: kp.kubeStateMetrics[name] for name in std.objectFields(kp.kubeStateMetrics) } +
{ ['kubernetes-' + name]: kp.kubernetesControlPlane[name] for name in std.objectFields(kp.kubernetesControlPlane) }
{ ['node-exporter-' + name]: kp.nodeExporter[name] for name in std.objectFields(kp.nodeExporter) } +
{ ['prometheus-' + name]: kp.prometheus[name] for name in std.objectFields(kp.prometheus) } +
{ ['prometheus-adapter-' + name]: kp.prometheusAdapter[name] for name in std.objectFields(kp.prometheusAdapter) }







