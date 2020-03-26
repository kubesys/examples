package com.example.edgedemo.core;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeAddress;
import io.fabric8.kubernetes.api.model.NodeCondition;
import io.fabric8.kubernetes.api.model.NodeStatus;
import io.fabric8.kubernetes.api.model.NodeSystemInfo;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.URLUtils;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

public class Devicelet {

    public final static String NODE_BASIC_INFO = "{\r\n" + "	\"apiVersion\": \"v1\",\r\n"
            + "	\"kind\": \"Node\",\r\n" + "	\"metadata\": {\r\n" + "		\"labels\": {\r\n"
            + "			\"host\": \"IMEINAME\"\r\n" + "		},\r\n" + "		\"name\": \"IMEINAME\"\r\n" + "	}\r\n"
            + "}";

    public final static String NODE_CONDITION_INFO = "[\r\n" + "	{\r\n"
            + "		\"lastHeartbeatTime\":\"CURRENTTIME\",\r\n" + "		\"lastTransitionTime\":\"CURRENTTIME\",\r\n"
            + "		\"message\":\"Calico is running on this node\",\r\n" + "		\"reason\":\"CalicoIsUp\",\r\n"
            + "		\"status\":\"False\",\r\n" + "		\"type\":\"NetworkUnavailable\"\r\n" + "	},\r\n" + "	{\r\n"
            + "		\"lastHeartbeatTime\":\"CURRENTTIME\",\r\n" + "		\"lastTransitionTime\":\"CURRENTTIME\",\r\n"
            + "		\"message\":\"kubelet has sufficient memory available\",\r\n"
            + "		\"reason\":\"KubeletHasSufficientMemory\",\r\n" + "		\"status\":\"False\",\r\n"
            + "		\"type\":\"MemoryPressure\"\r\n" + "	},\r\n" + "	{\r\n"
            + "		\"lastHeartbeatTime\":\"CURRENTTIME\",\r\n" + "		\"lastTransitionTime\":\"CURRENTTIME\",\r\n"
            + "		\"message\":\"kubelet has no disk pressure\",\r\n"
            + "		\"reason\":\"KubeletHasNoDiskPressure\",\r\n" + "		\"status\":\"False\",\r\n"
            + "		\"type\":\"DiskPressure\"\r\n" + "	},\r\n" + "	{\r\n"
            + "		\"lastHeartbeatTime\":\"CURRENTTIME\",\r\n" + "		\"lastTransitionTime\":\"CURRENTTIME\",\r\n"
            + "		\"message\":\"kubelet has sufficient PID available\",\r\n"
            + "		\"reason\":\"KubeletHasSufficientPID\",\r\n" + "		\"status\":\"False\",\r\n"
            + "		\"type\":\"PIDPressure\"\r\n" + "	},\r\n" + "	{\r\n"
            + "		\"lastHeartbeatTime\":\"CURRENTTIME\",\r\n" + "		\"lastTransitionTime\":\"CURRENTTIME\",\r\n"
            + "		\"message\":\"kubelet is posting ready status\",\r\n" + "		\"reason\":\"KubeletReady\",\r\n"
            + "		\"status\":\"True\",\r\n" + "		\"type\":\"Ready\"\r\n" + "	}\r\n" + "]";

    public final static String NODE_RESOURCE_INFO = "{\r\n" + "            \"cpu\": \"CPU\",\r\n"
            + "            \"ephemeral-storage\": \"STORAGE\",\r\n" + "            \"hugepages-1Gi\": \"0\",\r\n"
            + "            \"hugepages-2Mi\": \"0\",\r\n" + "            \"memory\": \"MEMORY\",\r\n"
            + "            \"pods\": \"110\"\r\n" + "        }";


    protected final DefaultKubernetesClient client = new DefaultKubernetesClient("http://39.106.124.113:8888");

    protected final NodeSystemInfo nodeInfo;

    protected final List<NodeAddress> nodeAddresses;

    public final static String CPU = "cpu";

    public final static String STORAGE = "ephemeral-storage";

    public final static String HUGEPAGE_1Gi = "hugepages-1Gi";

    public final static String HUGEPAGE_2Mi = "hugepages-2Mi";

    public final static String MEMORY = "memory";

    public final static String PODS = "pods";

    protected final  String nodeName;

    protected final String imei;

    public Devicelet(String imei) throws Exception {
        this.nodeName = SystemUtils.getDeviceName().toLowerCase() + "." + SystemUtils.getDeviceBrand().toLowerCase();
        this.imei = imei.toLowerCase();
        createNodeIfNeed();
        NodeStatus status = client.nodes().withName("ali3").get().getStatus();
        this.nodeInfo = initNodeInfo(status);
        this.nodeAddresses = initNodeAddress(status);
    }

    public JSONObject getNodeCapacity() {
        return getNodeCapacity(SystemUtils.getCPUNum(), SystemUtils.getMaxRAMSize(),
                SystemUtils.getDiskSize(), "110");
    }

    public JSONObject getNodeCapacity(String cpu, String memory, String storage, String pods) {
        String resource = NODE_RESOURCE_INFO.replaceAll("CPU", cpu).replaceAll("STORAGE", storage)
                                            .replaceAll("MEMORY", memory);
        System.out.println(resource);
        return JSON.parseObject(resource);
    }

    protected void createNodeIfNeed() throws Exception {
        try {
            Node node = client.nodes().withName(imei).get();
            if (node == null) {
                client.nodes().create(JSON.parseObject(NODE_BASIC_INFO.replaceAll("IMEINAME", imei), Node.class));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    protected List<NodeAddress> initNodeAddress(NodeStatus status) throws Exception {
        List<NodeAddress> adressList = status.getAddresses();
        for (NodeAddress address : adressList) {
            if (address.getAddress().equals("ali3")) {
                address.setAddress(nodeName);
            } else {
                address.setAddress(imei);
            }
        }
        return adressList;
    }

    protected NodeSystemInfo initNodeInfo(NodeStatus status) {
        NodeSystemInfo nsi = status.getNodeInfo();
        nsi.setContainerRuntimeVersion("edgeless://1.0.0");
        nsi.setSystemUUID(imei);
        nsi.setMachineID(imei);
        nsi.setBootID(imei);
        nsi.setOperatingSystem(SystemUtils.getSystemVersion());
        nsi.setKernelVersion(SystemUtils.getSystemModel());
        nsi.setOsImage(SystemUtils.getDeviceBrand() + "-" + SystemUtils.getSystemModel());
        return nsi;
    }

    public List<NodeCondition> getConditions() {
        String datetime = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
        return JSON.parseArray(NODE_CONDITION_INFO.replaceAll("CURRENTTIME", datetime)).toJavaList(NodeCondition.class);
    }


    public NodeSystemInfo getNodeInfo() {
        return nodeInfo;
    }

    public List<NodeAddress> getNodeAddresses() {
        return nodeAddresses;
    }

    public void updateNodeStatus() throws IOException {
        Node node = client.nodes().withName(imei).get();

        NodeStatus status = node.getStatus();
        status.setAddresses(getNodeAddresses());
        status.setNodeInfo(getNodeInfo());
        status.setConditions(getConditions());

        JSONObject jnode = (JSONObject) JSON.toJSON(node);
        JSONObject snode = (JSONObject) JSON.toJSON(status);
        snode.put("capacity", getNodeCapacity());
        snode.put("allocatable", getNodeCapacity());
        jnode.put("status", snode);

        System.out.println(JSON.toJSONString(jnode, true));

        /// api/v1/nodes/ali3
        final String statusUri = URLUtils.join(this.client.getMasterUrl().toString(), "api", "v1", "nodes",
                imei, "status");
        final RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"),
                JSON.toJSONString(jnode));
        client.getHttpClient().newCall(new Request.Builder().method("PUT", requestBody).url(statusUri).build())
                .execute().close();
    }

}
