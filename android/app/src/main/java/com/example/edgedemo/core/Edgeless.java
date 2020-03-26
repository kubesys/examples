package com.example.edgedemo.core;

public class Edgeless implements Runnable {

    protected final Devicelet let;

    public Edgeless(Devicelet let) {
        this.let = let;
    }
    @Override
    public void run() {
        while(true) {
            try {
                let.updateNodeStatus();
                Thread.sleep(10000);
            } catch (Exception ex) {
                ex.printStackTrace();

            }
        }
    }
}
