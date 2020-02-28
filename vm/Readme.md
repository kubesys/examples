# 研发背景
       
       本项目拟扩展Kubernetes支持虚拟机生命周期管理，本文介绍API详细列表，Java SDK示例，以及Kubernetes验证方法。
			 
1. Java SDK： https://github.com/kubesys/kubesys-jdk
	
# 示例的Kubernetes使用

1. 假设已经安装好Kubernetes，我们提供实现功能的YAML/JSON文件

对于Kubernetes而言，均执行以下操作

```
kubectl apply -f *.yaml
```

# Yaml/JSON文件与Java代码的关系

以下是通过ISO创建虚拟机的JSON文件。

```
{
  "apiVersion": "cloudplus.io/v1alpha3",
  "kind": "VirtualMachine",
  "metadata": {
    "name": "os1"
  },
  "spec": {
    "image": "CentOS7",
    "lifecycle": {
      "createAndStartVMFromISO": {
        "virt_type": "kvm",
        "memory": "1024",
        "vcpus": "1",
        "cdrom": "/var/lib/uus/CentOS-7-x86_64-DVD-1511.iso",
        "disk": "path=/dev/os1/os1",
        "network": "bridge=virbr0",
        "graphics": "vnc,listen=0.0.0.0",
        "noautoconsole": true
      }
    }
  }
}
```

其对应的Java代码如下：

```
ExtendedKubernetesClient client =
	ExtendedKubernetesClient.defaultConfig("config");
VirtualMachineImpl vmi = client.virtualMachines();
VirtualMachine vm = new VirtualMachine();     //对应JSON文件Kind
vm.setApiVersion("cloudplus.io/v1alpha3");    //对应JSON文件apiVersion
vm.setKind("VirtualMachine");                 //对应JSON文件Kind
ObjectMeta metadata = new ObjectMeta();        //对应JSON文件Metadata
metadata.setName("VM"); 
vm.setMetadata(metadata );
VirtualMachineSpec spec = new VirtualMachineSpec();     //对应JSON文件SPEC
Lifecycle lifecycle = new Lifecycle();                  //对应JSON文件SPEC中Lifecycle
createAndStartVMFromISO createAndStartVM = new createAndStartVMFromISO(); ////对应JSON文件SPEC中Lifecycle中createAndStartVMFromISO
{
        createAndStartVM.setVirt_type("kvm");  // 对应JSON中Virt_type
	createAndStartVM.setMemory("1024");     //对应memory
	createAndStartVM.setVcpus("1");        //对应vcpus
	createAndStartVM.setCdrom("/var/lib/uus/CentOS-7-x86_64-DVD-1511.iso"); //对应cdrom
	createAndStartVM.setDisk("path=/dev/os1/os1");           //对应disk
	createAndStartVM.setNetwork("bridge=virbr0");            //对应network
	createAndStartVM.setGraphics("vnc,listen=0.0.0.0");       //对应grapics
	createAndStartVM.setNoautoconsole(true);                //对应noautoconsole
}
lifecycle.setCreateAndStartVM(createAndStartVM)
spec.setLifecycle(lifecycle );
vm.setSpec(spec );
vmi.create(vm );
```
