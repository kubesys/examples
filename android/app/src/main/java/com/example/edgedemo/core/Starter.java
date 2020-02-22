package com.example.edgedemo.core;

import android.content.Context;

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
import io.fabric8.kubernetes.client.utils.URLUtils;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

public class Starter {

    protected final Context ctx;

    public Starter(Context ctx) {
        this.ctx = ctx;
    }

    public void start() {
        try {
            Devicelet let = new Devicelet(SystemUtils.getIMEI(ctx));
            Edgeless less = new Edgeless(let);
            Thread thread = new Thread(less);
            thread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
